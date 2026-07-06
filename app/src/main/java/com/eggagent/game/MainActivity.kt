package com.eggagent.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewModelScope
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ========== 蛋蛋类型 ==========
enum class EggType(val emoji: String, val eggName: String, val color: Color, val size: Int) {
    BLACK("\uD83E\uDD5A", "黑蛋", Color(0xFF2C2C2C), 1),
    YELLOW("\uD83E\uDD5A", "黄蛋", Color(0xFFFFD54F), 2),
    RED("\uD83E\uDD5A", "红蛋", Color(0xFFE53935), 3),
    BLUE("\uD83E\uDD5A", "蓝蛋", Color(0xFF1E88E5), 4),
    GREEN("\uD83E\uDD5A", "绿蛋", Color(0xFF43A047), 5);

    companion object {
        fun getByIndex(index: Int): EggType = entries[index % entries.size]
    }
}

// ========== 访问记录 ==========
data class VisitRecord(val visitNumber: Int, val eggCount: Int, val message: String)

// ========== 连续剧情事件 ==========
data class StoryEvent(
    val act: Int,           // 第几幕
    val title: String,      // 幕标题
    val dialogue: String,   // 兄弟台词
    val yourLine: String,   // 你的回应
    val narration: String,  // 旁白描述
    val minEggs: Int = 0,   // 建议最少蛋数
    val needsProsthetics: Boolean = false  // 是否需要假体
)

// ========== 游戏状态 ==========
data class GameState(
    val eggs: List<EggType> = emptyList(),
    val hasProsthetics: Boolean = false,      // 是否解锁假体
    val storyStage: Int = 0,                   // 当前剧情进度（0-6）
    val friendMessage: String = "点击「朋友来访」继续追剧！",
    val friendSubMessage: String = "",
    val friendName: String = "\uD83D\uDC64 兄弟",
    val visitHistory: List<VisitRecord> = emptyList(),
    val gameLog: List<String> = listOf("🎮 蛋蛋特工启动！📺 兄弟连续剧即将开播……"),
    val isMomCooking: Boolean = false,         // 被老妈煮蛋了？
    val isFriendHere: Boolean = false,         // 朋友正在对话？
    val showEggAnimation: Boolean = false,
    val eggAnimationType: EggType? = null,
    val showActTitle: Boolean = false,
    val currentActTitle: String = ""
)

// ========== ViewModel ==========
class EggGameViewModel : androidx.lifecycle.ViewModel() {

    var state by mutableStateOf(GameState())
        private set

    // 所有蛋蛋排列组合
    private val allEggTypes = EggType.entries.toList()

    // ===== 塞蛋 =====
    fun addEgg() {
        if (state.isMomCooking) return

        val currentEggs = state.eggs.toMutableList()

        if (currentEggs.size < 5) {
            // 塞入新蛋
            val newEgg = EggType.getByIndex(currentEggs.size)
            currentEggs.add(newEgg)
            addLog("\uD83E\uDD5A 你偷偷塞了一个 ${newEgg.eggName}！肚子${"😰".repeat(newEgg.size)}")

            state = state.copy(
                eggs = currentEggs,
                showEggAnimation = true,
                eggAnimationType = newEgg,
                gameLog = state.gameLog
            )

            // 自动隐藏动画
            viewModelScope.launch {
                delay(800)
                state = state.copy(showEggAnimation = false, eggAnimationType = null)
            }
        } else if (!state.hasProsthetics) {
            // 蛋塞满了！解锁假体
            state = state.copy(
                hasProsthetics = true,
                gameLog = state.gameLog + "\uD83D\uDCAA 蛋蛋军团已满！解锁假体装备：假手臂 + 假大腿 + 双下巴！"
            )
            checkMomEvent()
        } else {
            addLog("\uD83D\uDCA2 已经塞不下啦！再塞衣服要炸了！")
        }
    }

    // ===== 跳绳减肥 =====
    fun jumpRope() {
        if (state.isMomCooking) return

        val currentEggs = state.eggs.toMutableList()

        if (currentEggs.isNotEmpty()) {
            val removed = currentEggs.removeLast()
            addLog("\uD83C\uDFC3 你跳了半小时绳！${removed.eggName}掉了出来！肚子小了一圈～")

            state = state.copy(eggs = currentEggs)
        } else if (state.hasProsthetics) {
            state = state.copy(hasProsthetics = false)
            addLog("\uD83C\uDFC3 你把假体也拆了！恢复了本来面目！")
        } else {
            addLog("\uD83E\uDD14 你已经很瘦了，别再跳了！")
        }
    }

    // ========== 连续剧情 ==========
    private val storyEvents = listOf(
        StoryEvent(
            act = 1, title = "🥚 第一幕 · 黑蛋首秀",
            dialogue = "嗯？兄弟，你咋胖了这么多？肚子鼓起来好大一块！",
            yourLine = "唉，最近吃太多了，控制不住嘴……",
            narration = "朋友信了。他脸上全是关心。你回家把蛋掏出来，笑到打滚。",
            minEggs = 1
        ),
        StoryEvent(
            act = 2, title = "🏃 第二幕 · 瘦了",
            dialogue = "卧槽！你瘦了？！两天不见怎么瘦这么多？！牛逼啊你！",
            yourLine = "跳了两天绳，天赋异禀。",
            narration = "朋友信了。他比你还高兴。你回家，把换小的蛋掏出来，笑了十分钟。",
            minEggs = 0
        ),
        StoryEvent(
            act = 3, title = "😰 第三幕 · 反弹",
            dialogue = "……你怎么又胖了？？反弹能弹这么多的吗？！",
            yourLine = "唉，断了几天没跳，易胖体质，没办法……",
            narration = "朋友信了。他以为你真的很苦恼。你回家，把蛋掏出来，又笑了半天。",
            minEggs = 2
        ),
        StoryEvent(
            act = 4, title = "😱 第四幕 · 持续膨胀",
            dialogue = "你怎么又胖了？！兄弟，你去看看医生吧！这不对啊！！",
            yourLine = "看了，医生说是代谢问题……没事，我继续跳绳。",
            narration = "朋友信了。他开始为你担心了。你回家，把蛋掏出来，笑得肚子疼。",
            minEggs = 3
        ),
        StoryEvent(
            act = 5, title = "👽 第五幕 · 全面升级",
            dialogue = "（推开门，退了一步）……你谁？？？？",
            yourLine = "我啊！",
            narration = "朋友上下打量了你足足十秒，陷入了自我怀疑……",
            minEggs = 4
        ),
        StoryEvent(
            act = 6, title = "🤖 第六幕 · 最终形态",
            dialogue = "（沉默了十秒）……兄弟，你是吃了化肥吗？你已经……不像人了。",
            yourLine = "最近压力大，暴饮暴食……",
            narration = "朋友开始怀疑人生。你回家，五颗蛋加全套假体全部掏出来，在床上笑到抽筋。",
            minEggs = 5,
            needsProsthetics = true
        ),
        StoryEvent(
            act = 7, title = "♾️ 第七幕 · 蛋蛋轮回",
            dialogue = "（麻木地看了你一眼）哦，又胖了。随你吧……我去买口锅。",
            yourLine = "买锅干啥？",
            narration = "朋友：『给你妈做饭用，反正你迟早要露馅。』",
            minEggs = 0
        )
    )

    // ===== 朋友来访（连续剧模式）=====
    fun friendVisit() {
        if (state.isMomCooking) return
        if (state.isFriendHere) return

        val currentStage = state.storyStage
        val event = storyEvents[currentStage % storyEvents.size]
        val eggCount = state.eggs.size
        val newStage = currentStage + 1

        // 构建完整消息
        val fullMessage = buildString {
            appendLine(event.dialogue)
            appendLine()
            append("😅 你说：\"${event.yourLine}\"")
        }

        val narrationMsg = event.narration

        state = state.copy(
            storyStage = newStage,
            friendMessage = fullMessage,
            friendSubMessage = narrationMsg,
            isFriendHere = true,
            showActTitle = true,
            currentActTitle = event.title,
            visitHistory = state.visitHistory + VisitRecord(newStage, eggCount, event.title),
            gameLog = state.gameLog + """
📺━━━ ${event.title} ━━━
👤 兄弟：${event.dialogue}
😅 你：${event.yourLine}
📖 $narrationMsg
                """.trimIndent()
        )

        // 自动关闭
        viewModelScope.launch {
            delay(4000)
            state = state.copy(
                isFriendHere = false,
                showActTitle = false,
                currentActTitle = "",
                friendMessage = "点击「朋友来访」继续追剧！第${newStage + 1}集即将开播📺",
                friendSubMessage = ""
            )
            checkMomEvent()
        }
    }

    // ===== 检查老妈煮蛋事件 =====
    private fun checkMomEvent() {
        // 15% 概率触发老妈煮蛋（有蛋的情况下）
        if (state.eggs.isNotEmpty() && Random.nextFloat() < 0.15f) {
            viewModelScope.launch {
                delay(500)
                state = state.copy(
                    isMomCooking = true,
                    gameLog = state.gameLog + "\uD83D\uDC69\u200D\uD83C\uDF73 老妈：『冰箱里怎么这么多蛋？煮了当晚饭！』"
                )
                delay(2000)
                val cookedEggs = state.eggs.map { name -> name.eggName }
                state = state.copy(
                    eggs = emptyList(),
                    hasProsthetics = false,
                    isMomCooking = false,
                    gameLog = state.gameLog + "\uD83C\uDF73 老妈把你藏的蛋全煮了！${cookedEggs.joinToString("、")}变成了晚餐！\n${
                        cookedEggs.mapIndexed { i, name ->
                            "🍳 第${i + 1}颗：$name 水煮蛋"
                        }.joinToString("\n")
                    }"
                )
            }
        }
    }

    // ===== 重置游戏 =====
    fun resetGame() {
        state = GameState(
            gameLog = listOf("🔄 游戏已重置！重新开始蛋蛋特工之旅！")
        )
    }

    private fun addLog(msg: String) {
        state = state.copy(gameLog = state.gameLog + msg)
    }

    // 肚子大小的描述
    fun getBellyDescription(): String {
        val eggCount = state.eggs.size
        val hasExtra = state.hasProsthetics

        return when {
            state.isMomCooking -> "🍳 蛋被老妈煮了……"
            eggCount == 0 && !hasExtra -> "✨ 平坦小腹，完美身材！"
            eggCount == 1 -> "🥚 微微隆起……"
            eggCount == 2 -> "🥚🥚 有点鼓了……"
            eggCount == 3 -> "🥚🥚🥚 明显凸起！"
            eggCount == 4 -> "🥚🥚🥚🥚 好大的肚子！"
            eggCount == 5 && !hasExtra -> "🥚🥚🥚🥚🥚 巨无霸肚子！"
            hasExtra && eggCount >= 5 -> "🤖 你已经不是人了，是蛋蛋机器人！！"
            else -> "👀 状态未知"
        }
    }

    // 肚子视觉（每颗蛋用一个圆表示）
    fun getBellyVisuals(): List<Pair<Color, Float>> {
        return state.eggs.map { it.color to (0.3f + it.size * 0.15f) }
    }
}

// ========== Compose UI ==========
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFFF6F00),
                    secondary = Color(0xFFFFB300),
                    surface = Color(0xFFFFF8E1),
                    background = Color(0xFFFFF8E1)
                )
            ) {
                EggAgentGame()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EggAgentGame() {
    val viewModel = remember { EggGameViewModel() }
    val state = viewModel.state

    // 动画
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 自动滚动 log
    LaunchedEffect(state.gameLog.size) {
        listState.animateScrollToItem(state.gameLog.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF8E1), Color(0xFFFFE0B2), Color(0xFFFFCC80))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== 标题 =====
        Text(
            text = "\uD83E\uDD5A 蛋蛋特工 \uD83E\uDD5A",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE65100),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "兄弟永远不知道",
            fontSize = 14.sp,
            color = Color(0xFFBF360C),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ===== 蛋蛋计数 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 角色 + 肚子
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 身体
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 头 + 脸
                        Text(
                            text = when {
                                state.isMomCooking -> "\uD83D\uDE35\u200D\uD83D\uDCAB"
                                state.eggs.isEmpty() && !state.hasProsthetics -> "\uD83D\uDE0A"
                                state.eggs.size <= 2 -> "\uD83D\uDE42"
                                state.eggs.size <= 4 -> "\uD83D\uDE35"
                                state.hasProsthetics -> "\uD83E\uDD16"
                                else -> "\uD83D\uDE30"
                            },
                            fontSize = 40.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 肚子（动态显示蛋蛋）
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(
                                    (30 + state.eggs.size * 15 + (if (state.hasProsthetics) 20 else 0)).dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.eggs.isNotEmpty() || state.hasProsthetics) {
                                // 肚子背景（渐变圆）
                                val bellySize = state.eggs.size.coerceAtMost(5)
                                Surface(
                                    modifier = Modifier
                                        .size(
                                            (50 + bellySize * 15 + (if (state.hasProsthetics) 15 else 0)).dp
                                        )
                                        .scale(
                                            if (state.showEggAnimation) 1.2f else bounceScale
                                        ),
                                    shape = CircleShape,
                                    color = Color(0xFFFFCCBC),
                                    shadowElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        // 蛋蛋排列在肚子上
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            state.eggs.forEach { egg ->
                                                Text(
                                                    text = egg.emoji,
                                                    fontSize = (14 + egg.size * 2).sp,
                                                    modifier = Modifier.padding(horizontal = 1.dp)
                                                )
                                            }
                                        }
                                        if (state.hasProsthetics && state.eggs.size >= 5) {
                                            Text(
                                                text = "\uD83E\uDD16",
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 8.dp, y = (-8).dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "\uD83D\uDC4D",
                                    fontSize = 30.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 腿
                        Text(
                            text = if (state.hasProsthetics && state.eggs.size >= 5) "\uD83E\uDDB5\uD83E\uDDB6" else "\uD83D\uDC4D\uD83D\uDC4D",
                            fontSize = 20.sp
                        )
                    }

                    // 塞蛋动画
                    if (state.showEggAnimation && state.eggAnimationType != null) {
                        Text(
                            text = "${state.eggAnimationType.emoji} +1",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = state.eggAnimationType.color,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-20).dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 肚子状态描述
                Text(
                    text = viewModel.getBellyDescription(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4E342E),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "塞了 ${state.eggs.size}/5 颗蛋" +
                            if (state.hasProsthetics) " + 假体" else "",
                    fontSize = 14.sp,
                    color = Color(0xFF8D6E63)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 剧情标题横幅 =====
        if (state.showActTitle) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📺",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.currentActTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ===== 朋友对话框 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isFriendHere) Color(0xFFE3F2FD) else Color.White.copy(alpha = 0.7f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.isFriendHere) "\uD83D\uDC64" else "\uD83D\uDC4B",
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isFriendHere) "兄弟说：" else "等待来访...",
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                        Text(
                            text = state.friendMessage,
                            fontSize = 14.sp,
                            color = Color(0xFF212121)
                        )
                    }
                }
                // 旁白（来访结束后显示）
                if (!state.isFriendHere && state.friendSubMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "📖 ${state.friendSubMessage}",
                        fontSize = 12.sp,
                        color = Color(0xFF8D6E63),
                        modifier = Modifier.padding(start = 40.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 老妈煮蛋警告 =====
        if (state.isMomCooking) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "\uD83D\uDC69\u200D\uD83C\uDF73", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "老妈打开冰箱了！！！",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ===== 操作按钮 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 塞蛋按钮
            GameButton(
                text = "\uD83E\uDD5A 塞蛋",
                color = Color(0xFFFF8A65),
                enabled = !state.isMomCooking,
                onClick = { viewModel.addEgg() }
            )

            // 跳绳按钮
            GameButton(
                text = "\uD83C\uDFC3 跳绳",
                color = Color(0xFF81C784),
                enabled = !state.isMomCooking && (state.eggs.isNotEmpty() || state.hasProsthetics),
                onClick = { viewModel.jumpRope() }
            )

            // 朋友来访
            GameButton(
                text = "\uD83D\uDC64 来访",
                color = Color(0xFF64B5F6),
                enabled = !state.isMomCooking && !state.isFriendHere,
                onClick = { viewModel.friendVisit() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 重置按钮 =====
        OutlinedButton(
            onClick = { viewModel.resetGame() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8D6E63))
        ) {
            Text("\uD83D\uDD04 重新开始")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 来访记录 =====
        if (state.visitHistory.isNotEmpty()) {
            Text(
                text = "\uD83D\uDCDC 来访记录",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4E342E)
            )
            Spacer(modifier = Modifier.height(4.dp))

            state.visitHistory.reversed().forEach { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "第${record.visitNumber}集：${record.message}",
                        fontSize = 13.sp,
                        color = Color(0xFF5D4037),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 游戏日志 =====
        Text(
            text = "\uD83D\uDCCB 游戏日志",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4E342E)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5).copy(alpha = 0.9f))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                items(state.gameLog) { log ->
                    Text(
                        text = log,
                        fontSize = 12.sp,
                        color = Color(0xFF616161),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ========== 游戏按钮组件 ==========
@Composable
fun GameButton(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(56.dp)
            .width(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color(0xFFBDBDBD)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}