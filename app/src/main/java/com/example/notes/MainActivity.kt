package com.example.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import androidx.compose.runtime.collectAsState
import com.example.notes.ui.theme.NotesTheme
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotesTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NoteSortingApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSortingApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var currentCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val categories by db.noteDao().getAllCategories().collectAsState(initial = emptyList())
    val notes by if (currentCategory != null) {
        db.noteDao().getNotesByCategory(currentCategory!!.categoryName).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentCategory?.categoryName ?: "Note Categories") },
                navigationIcon = {
                    if (currentCategory != null) {
                        IconButton(onClick = { currentCategory = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentCategory != null) {
                CameraFAB(context) { uri ->
                    scope.launch(Dispatchers.IO) {
                        val path = saveImageToInternalStorage(context, uri)
                        if (path != null) {
                            db.noteDao().insertNote(
                                NoteEntity(
                                    filePath = path,
                                    categoryName = currentCategory!!.categoryName
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            if (currentCategory == null) {
                CategoryList(
                    categories = categories,
                    onCategoryClick = { currentCategory = it },
                    onAddCategory = { name ->
                        scope.launch(Dispatchers.IO) {
                            db.noteDao().insertCategory(CategoryEntity(name))
                        }
                    },
                    onDeleteCategory = { category ->
                        scope.launch(Dispatchers.IO) {
                            db.noteDao().deleteCategory(category)
                        }
                    }
                )
            } else {
                NoteGrid(
                    notes = notes,
                    onDeleteNote = { note ->
                        scope.launch(Dispatchers.IO) {
                            File(note.filePath).delete()
                            db.noteDao().deleteNote(note)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CameraFAB(context: Context, onPhotoCaptured: (Uri) -> Unit) {
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val tempFile = remember { File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg") }
    val tempUri = remember {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            onPhotoCaptured(tempUri)
        }
    }

    FloatingActionButton(
        onClick = {
            if (hasCameraPermission) {
                cameraLauncher.launch(tempUri)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    ) {
        Icon(Icons.Default.Add, contentDescription = "Take Photo")
    }
}

@Composable
fun CategoryList(
    categories: List<CategoryEntity>,
    onCategoryClick: (CategoryEntity) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = { newCategoryName = it },
                label = { Text("New Folder Name") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName)
                        newCategoryName = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Create")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(categories) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(category) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = category.categoryName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onDeleteCategory(category) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NoteGrid(notes: List<NoteEntity>, onDeleteNote: (NoteEntity) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(notes) { note ->
            Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box {
                    val bitmap = remember(note.filePath) {
                        BitmapFactory.decodeFile(note.filePath)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    IconButton(
                        onClick = { onDeleteNote(note) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Entity
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val noteId: Long = 0,
    val filePath: String,
    val categoryName: String
)

@Entity
data class CategoryEntity(
    @PrimaryKey val categoryName: String
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM CategoryEntity")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT * FROM NoteEntity WHERE categoryName = :categoryName")
    fun getNotesByCategory(categoryName: String): Flow<List<NoteEntity>>

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}

@Database(entities = [NoteEntity::class, CategoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "note_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    val inputStream = context.contentResolver.openInputStream(uri)
    val fileName = "IMG_${System.currentTimeMillis()}.jpg"
    val file = File(context.filesDir, fileName)

    return try {
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        outputStream.close()
        inputStream?.close()
        file.absolutePath // This is what you save to the DB
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
