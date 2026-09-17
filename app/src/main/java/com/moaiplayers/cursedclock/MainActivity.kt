package com.moaiplayers.cursedclock

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moaiplayers.cursedclock.ui.theme.CursedclockTheme
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Player { WHITE, BLACK }
enum class TimeUnit { MINUTES, SECONDS }

data class GameRecord(
    val id: Long,
    val date: String,
    val whiteTime: String,
    val blackTime: String,
    val winner: String
)

// ===== Zapis / odczyt historii =====

private const val HISTORY_FILE = "game_history.txt"

fun loadHistory(context: Context): MutableList<GameRecord> {
    val file = File(context.filesDir, HISTORY_FILE)
    if (!file.exists()) return mutableListOf()
    val result = mutableListOf<GameRecord>()
    file.forEachLine { line ->
        if (line.isNotBlank()) {
            val parts = line.split("|")
            if (parts.size == 5) {
                result.add(
                    GameRecord(
                        id = parts[0].toLongOrNull() ?: 0L,
                        date = parts[1],
                        whiteTime = parts[2],
                        blackTime = parts[3],
                        winner = parts[4]
                    )
                )
            }
        }
    }
    result.sortByDescending { it.id }
    return result
}

fun saveHistory(context: Context, history: List<GameRecord>) {
    val file = File(context.filesDir, HISTORY_FILE)
    file.writeText(
        history.joinToString("\n") { record ->
            "${record.id}|${record.date}|${record.whiteTime}|${record.blackTime}|${record.winner}"
        }
    )
}

// ===== Aplikacja =====

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CursedclockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CursedClockApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun CursedClockApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var gameStartSeconds by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    val history = remember { mutableStateListOf<GameRecord>().apply { addAll(loadHistory(context)) } }

    when {
        showHistory -> {
            HistoryScreen(
                history = history,
                onDelete = { record ->
                    history.remove(record)
                    saveHistory(context, history)
                },
                onDeleteMultiple = { records ->
                    history.removeAll(records)
                    saveHistory(context, history)
                },
                onClearAll = {
                    history.clear()
                    saveHistory(context, history)
                },
                onBack = { showHistory = false },
                modifier = modifier
            )
        }
        gameStartSeconds == null -> {
            TimeSelectionScreen(
                onTimeSelected = { minutes -> gameStartSeconds = minutes * 60 },
                onShowHistory = { showHistory = true },
                modifier = modifier
            )
        }
        else -> {
            ChessClockScreen(
                startSeconds = gameStartSeconds!!,
                onSaveAndExit = { whiteTime, blackTime, winner ->
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    val newRecord = GameRecord(
                        id = System.currentTimeMillis(),
                        date = dateFormat.format(Date()),
                        whiteTime = whiteTime,
                        blackTime = blackTime,
                        winner = winner
                    )
                    history.add(0, newRecord)
                    saveHistory(context, history)
                    gameStartSeconds = null
                },
                onExitWithoutSaving = { gameStartSeconds = null },
                modifier = modifier
            )
        }
    }
}

@Composable
fun TimeSelectionScreen(
    onTimeSelected: (Int) -> Unit,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Wybierz czas",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        TimeButton("30 minut", 30, onTimeSelected)
        Spacer(Modifier.height(12.dp))
        TimeButton("15 minut", 15, onTimeSelected)
        Spacer(Modifier.height(12.dp))
        TimeButton("5 minut", 5, onTimeSelected)
        Spacer(Modifier.height(12.dp))
        TimeButton("3 minuty", 3, onTimeSelected)

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onShowHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF444444),
                contentColor = Color.White
            )
        ) {
            Text("HISTORIA GIER", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TimeButton(label: String, minutes: Int, onTimeSelected: (Int) -> Unit) {
    Button(
        onClick = { onTimeSelected(minutes) },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2ECC40),
            contentColor = Color.Black
        )
    ) {
        Text(text = label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HistoryScreen(
    history: List<GameRecord>,
    onDelete: (GameRecord) -> Unit,
    onDeleteMultiple: (List<GameRecord>) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    var pendingDelete by remember { mutableStateOf<GameRecord?>(null) }
    var confirmDeleteMultiple by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (selectionMode) {
                        selectionMode = false
                        selectedIds.clear()
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF444444),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (selectionMode) "Anuluj" else "← Wróć",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = if (selectionMode) "Zaznaczono: ${selectedIds.size}" else "Historia gier",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            if (!selectionMode && history.isNotEmpty()) {
                Button(
                    onClick = {
                        selectionMode = true
                        selectedIds.clear()
                    },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC40),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Zaznacz", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (selectionMode && selectedIds.isNotEmpty()) {
                Button(
                    onClick = { confirmDeleteMultiple = true },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4136),
                        contentColor = Color.White
                    )
                ) {
                    Text("Usuń (${selectedIds.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak zapisanych gier",
                    fontSize = 18.sp,
                    color = Color(0xFF999999)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { record ->
                    HistoryItem(
                        record = record,
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(record.id),
                        onToggleSelect = {
                            if (selectedIds.contains(record.id)) selectedIds.remove(record.id)
                            else selectedIds.add(record.id)
                        },
                        onDelete = { pendingDelete = record }
                    )
                }
            }
        }

        if (!selectionMode && history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { confirmClearAll = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF4136),
                    contentColor = Color.White
                )
            ) {
                Text("WYCZYŚĆ CAŁĄ HISTORIĘ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    pendingDelete?.let { record ->
        ConfirmDialog(
            title = "Usunąć ten wpis?",
            message = "${record.date}\nWhite: ${record.whiteTime}  Black: ${record.blackTime}",
            onConfirm = {
                onDelete(record)
                pendingDelete = null
            },
            onCancel = { pendingDelete = null }
        )
    }

    if (confirmDeleteMultiple) {
        ConfirmDialog(
            title = "Usunąć ${selectedIds.size} wpisów?",
            message = "Tej operacji nie można cofnąć.",
            onConfirm = {
                val records = history.filter { selectedIds.contains(it.id) }
                onDeleteMultiple(records)
                selectedIds.clear()
                selectionMode = false
                confirmDeleteMultiple = false
            },
            onCancel = { confirmDeleteMultiple = false }
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "Usunąć całą historię?",
            message = "Wszystkie ${history.size} wpisów zostanie usuniętych.",
            onConfirm = {
                onClearAll()
                confirmClearAll = false
            },
            onCancel = { confirmClearAll = false }
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .clickable { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF2A2A2A))
                .padding(24.dp)
                .clickable { /* blokuj */ },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF4136),
                    contentColor = Color.White
                )
            ) {
                Text("TAK, usuń", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF444444),
                    contentColor = Color.White
                )
            ) {
                Text("Anuluj", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HistoryItem(
    record: GameRecord,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF3A4A3A) else Color(0xFF2A2A2A))
            .clickable {
                if (selectionMode) onToggleSelect()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.date,
                fontSize = 12.sp,
                color = Color(0xFF999999)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "White: ${record.whiteTime}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEEEEEE)
                )
                Text(
                    text = "Black: ${record.blackTime}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEEEEEE)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Zwycięzca: ${record.winner}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2ECC40)
            )
        }

        if (!selectionMode) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .width(70.dp)
                    .height(70.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF4136),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Usuń", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChessClockScreen(
    startSeconds: Int,
    onSaveAndExit: (whiteTime: String, blackTime: String, winner: String) -> Unit,
    onExitWithoutSaving: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    var whiteSeconds by remember { mutableIntStateOf(startSeconds) }
    var blackSeconds by remember { mutableIntStateOf(startSeconds) }
    var activePlayer by remember { mutableStateOf(Player.WHITE) }
    var isPaused by remember { mutableStateOf(false) }
    var soundPlayed by remember { mutableStateOf(false) }
    var godModeOpen by remember { mutableStateOf(false) }
    var endGameDialogOpen by remember { mutableStateOf(false) }

    val someoneLost = whiteSeconds <= 0 || blackSeconds <= 0
    val clockStopped = isPaused || godModeOpen || endGameDialogOpen

    LaunchedEffect(someoneLost) {
        if (someoneLost && !soundPlayed) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)

            val pattern = longArrayOf(0, 700, 700)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }

            soundPlayed = true
        }
    }

    // Zatrzymaj wibracje i dźwięk przy wyjściu z ekranu
    DisposableEffect(Unit) {
        onDispose {
            vibrator.cancel()
            toneGenerator.stopTone()
        }
    }

    LaunchedEffect(activePlayer, clockStopped, someoneLost) {
        while (!clockStopped && !someoneLost) {
            delay(1000L)
            if (activePlayer == Player.WHITE) {
                if (whiteSeconds > 0) whiteSeconds--
            } else {
                if (blackSeconds > 0) blackSeconds--
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            PlayerClock(
                name = "White",
                timeText = formatTime(whiteSeconds),
                opponentTimeText = formatTime(blackSeconds),
                background = Color(0xFFEEEEEE),
                textColor = if (whiteSeconds <= 10) Color(0xFFCC0000) else Color(0xFF222222),
                opponentTextColor = Color(0xFF666666),
                isActive = activePlayer == Player.WHITE && !clockStopped && !someoneLost,
                rotateText = true,
                barAtTop = false,
                onClick = {
                    if (!clockStopped && !someoneLost && activePlayer != Player.BLACK) {
                        activePlayer = Player.BLACK
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { godModeOpen = true },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC40),
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "GOD MODE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { isPaused = !isPaused },
                    enabled = !someoneLost,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) Color(0xFF2ECC40) else Color(0xFFFFAA00),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = if (isPaused) "Wznów" else "Pauza",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { endGameDialogOpen = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4136),
                        contentColor = Color.White
                    )
                ) {
                    Text("Zakończ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            PlayerClock(
                name = "Black",
                timeText = formatTime(blackSeconds),
                opponentTimeText = formatTime(whiteSeconds),
                background = Color(0xFF222222),
                textColor = if (blackSeconds <= 10) Color(0xFFFF4444) else Color(0xFFEEEEEE),
                opponentTextColor = Color(0xFF999999),
                isActive = activePlayer == Player.BLACK && !clockStopped && !someoneLost,
                rotateText = false,
                barAtTop = true,
                onClick = {
                    if (!clockStopped && !someoneLost && activePlayer != Player.WHITE) {
                        activePlayer = Player.WHITE
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        if (godModeOpen) {
            GodModeOverlay(
                whiteSeconds = whiteSeconds,
                blackSeconds = blackSeconds,
                startSeconds = startSeconds,
                onAddTime = { player, minutes ->
                    if (player == Player.WHITE) whiteSeconds += minutes * 60
                    else blackSeconds += minutes * 60
                },
                onResetTime = { player ->
                    if (player == Player.WHITE) whiteSeconds = startSeconds
                    else blackSeconds = startSeconds
                },
                onSetExactTime = { player, seconds ->
                    if (player == Player.WHITE) whiteSeconds = seconds
                    else blackSeconds = seconds
                },
                onClose = { godModeOpen = false }
            )
        }

        if (endGameDialogOpen) {
            EndGameDialog(
                whiteSeconds = whiteSeconds,
                blackSeconds = blackSeconds,
                onSave = {
                    vibrator.cancel()
                    toneGenerator.stopTone()
                    val winner = when {
                        whiteSeconds <= 0 && blackSeconds <= 0 -> "Remis"
                        whiteSeconds <= 0 -> "Black"
                        blackSeconds <= 0 -> "White"
                        whiteSeconds < blackSeconds -> "Black"
                        blackSeconds < whiteSeconds -> "White"
                        else -> "Remis"
                    }
                    onSaveAndExit(
                        formatTime(whiteSeconds),
                        formatTime(blackSeconds),
                        winner
                    )
                },
                onDontSave = {
                    vibrator.cancel()
                    toneGenerator.stopTone()
                    onExitWithoutSaving()
                },
                onCancel = { endGameDialogOpen = false }
            )
        }
    }
}

@Composable
fun EndGameDialog(
    whiteSeconds: Int,
    blackSeconds: Int,
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .clickable { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF2A2A2A))
                .padding(24.dp)
                .clickable { /* blokuj */ },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Zakończyć grę?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Biały: ${formatTime(whiteSeconds)}",
                fontSize = 18.sp,
                color = Color(0xFFEEEEEE)
            )
            Text(
                text = "Czarny: ${formatTime(blackSeconds)}",
                fontSize = 18.sp,
                color = Color(0xFFEEEEEE),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Zapisać wynik do historii?",
                fontSize = 16.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2ECC40),
                    contentColor = Color.Black
                )
            ) {
                Text("TAK — zapisz", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onDontSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF4136),
                    contentColor = Color.White
                )
            ) {
                Text("NIE — nie zapisuj", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF444444),
                    contentColor = Color.White
                )
            ) {
                Text("Anuluj", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GodModeOverlay(
    whiteSeconds: Int,
    blackSeconds: Int,
    startSeconds: Int,
    onAddTime: (Player, Int) -> Unit,
    onResetTime: (Player) -> Unit,
    onSetExactTime: (Player, Int) -> Unit,
    onClose: () -> Unit
) {
    var selectedPlayer by remember { mutableStateOf(Player.WHITE) }
    var customTimeText by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf(TimeUnit.MINUTES) }
    var unitDropdownOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF2A2A2A))
                .padding(20.dp)
                .clickable { /* blokuj */ },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GOD MODE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2ECC40),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Zegar zatrzymany",
                fontSize = 12.sp,
                color = Color(0xFFFFAA00),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedPlayer = Player.WHITE },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPlayer == Player.WHITE) Color(0xFF2ECC40) else Color(0xFF444444),
                        contentColor = if (selectedPlayer == Player.WHITE) Color.Black else Color.White
                    )
                ) {
                    Text("WHITE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { selectedPlayer = Player.BLACK },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPlayer == Player.BLACK) Color(0xFF2ECC40) else Color(0xFF444444),
                        contentColor = if (selectedPlayer == Player.BLACK) Color.Black else Color.White
                    )
                ) {
                    Text("BLACK", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Wybrany: ${if (selectedPlayer == Player.WHITE) "White" else "Black"}  |  " +
                        formatTime(if (selectedPlayer == Player.WHITE) whiteSeconds else blackSeconds),
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            GodButton("+1 minuta") { onAddTime(selectedPlayer, 1) }
            Spacer(Modifier.height(8.dp))
            GodButton("+5 minut") { onAddTime(selectedPlayer, 5) }
            Spacer(Modifier.height(8.dp))
            GodButton("−1 minuta") { onAddTime(selectedPlayer, -1) }
            Spacer(Modifier.height(8.dp))
            GodButton("Reset do ${formatTime(startSeconds)}") { onResetTime(selectedPlayer) }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF555555))
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Custom time",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2ECC40),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customTimeText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            customTimeText = newValue
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("np. 15", color = Color(0xFF999999)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Box {
                    Button(
                        onClick = { unitDropdownOpen = true },
                        modifier = Modifier
                            .width(110.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF444444),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (customUnit == TimeUnit.MINUTES) "minuty" else "sekundy",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = unitDropdownOpen,
                        onDismissRequest = { unitDropdownOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("minuty") },
                            onClick = {
                                customUnit = TimeUnit.MINUTES
                                unitDropdownOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("sekundy") },
                            onClick = {
                                customUnit = TimeUnit.SECONDS
                                unitDropdownOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val value = customTimeText.toIntOrNull() ?: return@Button
                    val seconds = if (customUnit == TimeUnit.MINUTES) value * 60 else value
                    onSetExactTime(selectedPlayer, seconds)
                    customTimeText = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2ECC40),
                    contentColor = Color.Black
                )
            ) {
                Text("USTAW", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            GodButton("Zamknij", color = Color(0xFFFF4136)) { onClose() }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "White: ${formatTime(whiteSeconds)}   Black: ${formatTime(blackSeconds)}",
                fontSize = 12.sp,
                color = Color(0xFF999999)
            )
        }
    }
}

@Composable
fun GodButton(
    label: String,
    color: Color = Color(0xFF444444),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White
        )
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlayerClock(
    name: String,
    timeText: String,
    opponentTimeText: String,
    background: Color,
    textColor: Color,
    opponentTextColor: Color,
    isActive: Boolean,
    rotateText: Boolean,
    barAtTop: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(background)
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .then(if (rotateText) Modifier.rotate(180f) else Modifier)
        ) {
            Text(
                text = name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = timeText,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = "Opp: $opponentTimeText",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = opponentTextColor,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(if (barAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .height(12.dp)
                .background(if (isActive) Color(0xFF2ECC40) else Color(0xFFFF4136))
        )
    }
}

fun formatTime(totalSeconds: Int): String {
    val safe = if (totalSeconds < 0) 0 else totalSeconds
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun ChessClockPreview() {
    CursedclockTheme {
        CursedClockApp()
    }
}