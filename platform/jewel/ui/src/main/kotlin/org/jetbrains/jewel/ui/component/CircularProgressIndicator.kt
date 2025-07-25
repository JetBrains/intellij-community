package org.jetbrains.jewel.ui.component

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import org.jetbrains.jewel.ui.theme.circularProgressStyle
import org.jetbrains.jewel.ui.util.toRgbaHexString

@Composable
public fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    style: CircularProgressStyle = JewelTheme.circularProgressStyle,
    loadingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    CircularProgressIndicatorImpl(
        modifier = modifier,
        iconSize = DpSize(16.dp, 16.dp),
        style = style,
        dispatcher = loadingDispatcher,
        frameRetriever = { color -> SpinnerProgressIconGenerator.Small.generateSvgFrames(color.toRgbaHexString()) },
    )
}

@Composable
public fun CircularProgressIndicatorBig(
    modifier: Modifier = Modifier,
    style: CircularProgressStyle = JewelTheme.circularProgressStyle,
    loadingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    CircularProgressIndicatorImpl(
        modifier = modifier,
        iconSize = DpSize(32.dp, 32.dp),
        style = style,
        dispatcher = loadingDispatcher,
        frameRetriever = { color -> SpinnerProgressIconGenerator.Big.generateSvgFrames(color.toRgbaHexString()) },
    )
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun CircularProgressIndicatorImpl(
    iconSize: DpSize,
    style: CircularProgressStyle,
    dispatcher: CoroutineDispatcher,
    frameRetriever: (Color) -> List<String>,
    modifier: Modifier = Modifier,
) {
    val defaultColor = if (JewelTheme.isDark) Color(0xFF6F737A) else Color(0xFFA8ADBD)

    val density = LocalDensity.current
    val frames by
        produceState(emptyList(), density, style.color, defaultColor, dispatcher, frameRetriever) {
            value =
                withContext(dispatcher) {
                    frameRetriever(style.color.takeOrElse { defaultColor }).map {
                        it.toByteArray().decodeToSvgPainter(density)
                    }
                }
        }

    if (frames.isEmpty()) {
        Box(modifier.size(iconSize))
    } else {
        val framesCount = frames.size
        val transition = rememberInfiniteTransition("CircularProgressIndicator")
        val currentIndex by
            transition.animateValue(
                initialValue = 0,
                targetValue = framesCount,
                typeConverter = Int.VectorConverter,
                animationSpec =
                    InfiniteRepeatableSpec(
                        tween(
                            easing = LinearEasing,
                            durationMillis = (style.frameTime.inWholeMilliseconds * framesCount).toInt(),
                        ),
                        repeatMode = RepeatMode.Restart,
                    ),
            )

        val currentPainter = frames[currentIndex]
        Icon(modifier = modifier.size(iconSize), painter = currentPainter, contentDescription = null)
    }
}

private object SpinnerProgressIconGenerator {
    private val opacityList = listOf(1.0f, 0.93f, 0.78f, 0.69f, 0.62f, 0.48f, 0.38f, 0.0f)

    private fun StringBuilder.closeRoot() = append("</svg>")

    private fun StringBuilder.openRoot(sizePx: Int) =
        append(
            "<svg width=\"$sizePx\" height=\"$sizePx\" viewBox=\"0 0 16 16\" fill=\"none\" " +
                "xmlns=\"http://www.w3.org/2000/svg\">"
        )

    private fun generateSvgIcon(size: Int, opacityListShifted: List<Float>, colorHex: String) = buildString {
        openRoot(size)
        elements(colorHex = colorHex, opacityList = opacityListShifted)
        closeRoot()
    }

    private fun StringBuilder.elements(colorHex: String, opacityList: List<Float>) {
        appendLine()
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[0]}" x="7" y="1" width="2" height="4" rx="1"/>"""
        )
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[1]}" x="2.34961" y="3.76416" width="2" height="4" rx="1""""
        )
        appendLine("""          transform="rotate(-45 2.34961 3.76416)"/>""")
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[2]}" x="1" y="7" width="4" height="2" rx="1"/>"""
        )
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[3]}" x="5.17871" y="9.40991" width="2" height="4" rx="1""""
        )
        appendLine("""          transform="rotate(45 5.17871 9.40991)"/>""")
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[4]}" x="7" y="11" width="2" height="4" rx="1"/>"""
        )
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[5]}" x="9.41016" y="10.8242" width="2" height="4" rx="1""""
        )
        appendLine("""          transform="rotate(-45 9.41016 10.8242)"/>""")
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[6]}" x="11" y="7" width="4" height="2" rx="1"/>"""
        )
        appendLine(
            """    <rect fill="$colorHex" opacity="${opacityList[7]}" x="12.2383" y="2.3501" width="2" height="4" rx="1""""
        )
        appendLine("""          transform="rotate(45 12.2383 2.3501)"/>""")
    }

    object Small {
        fun generateSvgFrames(colorHex: String): List<String> = buildList {
            val opacityListShifted = opacityList.toMutableList()
            repeat(opacityList.count()) {
                add(generateSvgIcon(size = 16, colorHex = colorHex, opacityListShifted = opacityListShifted))
                opacityListShifted.shtr()
            }
        }
    }

    object Big {
        fun generateSvgFrames(colorHex: String): List<String> = buildList {
            val opacityListShifted = opacityList.toMutableList()
            repeat(opacityList.count()) {
                add(generateSvgIcon(size = 32, colorHex = colorHex, opacityListShifted = opacityListShifted))
                opacityListShifted.shtr()
            }
        }
    }

    private fun <T> MutableList<T>.shtr() {
        add(first())
        removeFirst()
    }
}
