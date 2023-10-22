package org.jetbrains.jewel.ui.util

object SpinnerProgressIconGenerator {

    private val opacityList = listOf(1.0f, 0.93f, 0.78f, 0.69f, 0.62f, 0.48f, 0.38f, 0.0f)

    private val rotations = listOf(0, -45, 0, 45, 0, -45, 0, 45)

    // for a 16x16 icon
    internal val points = listOf(
        7f to 1f,
        2.34961f to 3.76416f,
        1f to 7f,
        5.17871f to 9.40991f,
        7f to 11f,
        9.41016f to 10.8242f,
        11f to 7f,
        12.2383f to 2.34961f,
    )

    private fun StringBuilder.closeTag() = append("</svg>")
    private fun StringBuilder.openTag(sizePx: Int) = append(
        "<svg width=\"$sizePx\" height=\"$sizePx\" viewBox=\"0 0 $sizePx $sizePx\" fill=\"none\" " +
            "xmlns=\"http://www.w3.org/2000/svg\">",
    )

    private fun getSvgPlainTextIcon(
        step: Int,
        pointList: List<Pair<Float, Float>>,
        colorHex: String,
        thickness: Int = 2,
        length: Int = 4,
        cornerRadius: Int = 1,
    ) =
        buildString {
            openTag(16)
            appendLine()
            for (index in 0..opacityList.lastIndex) {
                val currentIndex = (index + step + 1) % opacityList.size
                val currentOpacity = opacityList[currentIndex]
                if (currentOpacity == 0.0f) continue
                drawElement(
                    colorHex = colorHex,
                    opacity = currentOpacity,
                    x = pointList[index].first,
                    y = pointList[index].second,
                    width = thickness,
                    height = length,
                    rx = cornerRadius,
                    rotation = rotations[index],
                )
            }
            closeTag()
            appendLine()
        }

    private fun StringBuilder.drawElement(
        colorHex: String,
        opacity: Float,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        rx: Int,
        rotation: Int,
    ) {
        append("<rect fill=\"${colorHex}\" opacity=\"$opacity\" x=\"$x\" y=\"$y\" width=\"$width\" height=\"$height\" rx=\"$rx\"")
        if (rotation != 0) append(" transform=\"rotate($rotation $x $y)\"")
        append("/>\n")
    }

    internal fun getPlainTextSvgList(colorHex: String, size: Int) = buildList {
        val scaleFactor = size / 16f
        for (index in 0..opacityList.lastIndex) {
            if (size == 16) {
                add(getSvgPlainTextIcon(index, points, colorHex))
            } else {
                add(
                    getSvgPlainTextIcon(
                        index,
                        points.map { it.first * scaleFactor to it.second * scaleFactor },
                        colorHex,
                        thickness = (2 * scaleFactor).toInt().coerceAtLeast(1),
                        length = (4 * scaleFactor).toInt().coerceAtLeast(1),
                        cornerRadius = (2 * scaleFactor).toInt().coerceAtLeast(1),
                    ),
                )
            }
        }
    }

    object Small {

        fun generateRawSvg(colorHex: String) = getPlainTextSvgList(colorHex = colorHex, size = 16)
    }

    object Big {

        fun generateRawSvg(colorHex: String) =
            getPlainTextSvgList(colorHex = colorHex, size = 32)
    }
}
