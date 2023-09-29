package org.jetbrains.jewel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.jewel.styling.CircularProgressStyle
import org.jetbrains.jewel.util.toHexString

@Composable
fun CircularProgressIndicator(
    svgLoader: SvgLoader,
    modifier: Modifier = Modifier,
    style: CircularProgressStyle = IntelliJTheme.circularProgressStyle,
) {
    CircularProgressIndicatorImpl(
        modifier = modifier,
        svgLoader = svgLoader,
        iconSize = DpSize(16.dp, 16.dp),
        style = style,
        frameRetriever = { color -> SpinnerProgressIconGenerator.Small.generateSvgFrames(color.toHexString()) },
    )
}

@Composable
fun CircularProgressIndicatorBig(
    svgLoader: SvgLoader,
    modifier: Modifier = Modifier,
    style: CircularProgressStyle = IntelliJTheme.circularProgressStyle,
) {
    CircularProgressIndicatorImpl(
        modifier = modifier,
        svgLoader = svgLoader,
        iconSize = DpSize(32.dp, 32.dp),
        style = style,
        frameRetriever = { color -> SpinnerProgressIconGenerator.Big.generateSvgFrames(color.toHexString()) },
    )
}

@Composable
private fun CircularProgressIndicatorImpl(
    modifier: Modifier = Modifier,
    svgLoader: SvgLoader,
    iconSize: DpSize,
    style: CircularProgressStyle,
    frameRetriever: (Color) -> List<String>,
) {
    val defaultColor = if (IntelliJTheme.isDark) Color(0xFF6F737A) else Color(0xFFA8ADBD)
    var isFrameReady by remember { mutableStateOf(false) }
    var currentFrame: Pair<String, Int> by remember { mutableStateOf("" to 0) }

    if (!isFrameReady) {
        Box(modifier.size(iconSize))
    } else {
        Icon(
            modifier = modifier.size(iconSize),
            painter = svgLoader.loadRawSvg(
                currentFrame.first,
                "circularProgressIndicator_frame_${currentFrame.second}",
            ),
            contentDescription = null,
        )
    }

    LaunchedEffect(style.color) {
        val frames = frameRetriever(style.color.takeOrElse { defaultColor })
        while (true) {
            for (i in 0 until frames.size) {
                currentFrame = frames[i] to i
                isFrameReady = true
                delay(style.frameTime.inWholeMilliseconds)
            }
        }
    }
}

object SpinnerProgressIconGenerator {

    private val opacityList = listOf(1.0f, 0.93f, 0.78f, 0.69f, 0.62f, 0.48f, 0.38f, 0.0f)

    private fun StringBuilder.closeRoot() = append("</svg>")
    private fun StringBuilder.openRoot(sizePx: Int) = append(
        "<svg width=\"$sizePx\" height=\"$sizePx\" viewBox=\"0 0 16 16\" fill=\"none\" " +
            "xmlns=\"http://www.w3.org/2000/svg\">",
    )

    private fun generateSvgIcon(
        size: Int,
        opacityListShifted: List<Float>,
        colorHex: String,
    ) =
        buildString {
            openRoot(size)
            elements(
                colorHex = colorHex,
                opacityList = opacityListShifted,
            )
            closeRoot()
        }

    private fun StringBuilder.elements(
        colorHex: String,
        opacityList: List<Float>,
    ) {
        append(
            "\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[0]}\" x=\"7\" y=\"1\" width=\"2\" height=\"4\" rx=\"1\"/>\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[1]}\" x=\"2.34961\" y=\"3.76416\" width=\"2\" height=\"4\" rx=\"1\"\n" +
                "          transform=\"rotate(-45 2.34961 3.76416)\"/>\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[2]}\" x=\"1\" y=\"7\" width=\"4\" height=\"2\" rx=\"1\"/>\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[3]}\" x=\"5.17871\" y=\"9.40991\" width=\"2\" height=\"4\" rx=\"1\"\n" +
                "          transform=\"rotate(45 5.17871 9.40991)\"/>\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[4]}\" x=\"7\" y=\"11\" width=\"2\" height=\"4\" rx=\"1\"/>\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[5]}\" x=\"9.41016\" y=\"10.8242\" width=\"2\" height=\"4\" rx=\"1\"\n" +
                "          transform=\"rotate(-45 9.41016 10.8242)\"/>\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[6]}\" x=\"11\" y=\"7\" width=\"4\" height=\"2\" rx=\"1\"/>\n" +
                "    <rect fill=\"$colorHex\" opacity=\"${opacityList[7]}\" x=\"12.2383\" y=\"2.3501\" width=\"2\" height=\"4\" rx=\"1\"\n" +
                "          transform=\"rotate(45 12.2383 2.3501)\"/>\n",
        )
    }

    object Small {

        fun generateSvgFrames(colorHex: String) = buildList {
            val opacityListShifted = opacityList.toMutableList()
            repeat(opacityList.count()) {
                add(
                    generateSvgIcon(
                        size = 16,
                        colorHex = colorHex,
                        opacityListShifted = opacityListShifted,
                    ),
                )
                opacityListShifted.shtr()
            }
        }
    }

    object Big {

        fun generateSvgFrames(colorHex: String) = buildList {
            val opacityListShifted = opacityList.toMutableList()
            repeat(opacityList.count()) {
                add(
                    generateSvgIcon(
                        size = 32,
                        colorHex = colorHex,
                        opacityListShifted = opacityListShifted,
                    ),
                )
                opacityListShifted.shtr()
            }
        }
    }

    private fun <T> MutableList<T>.shtr() {
        add(first())
        removeFirst()
    }
}
