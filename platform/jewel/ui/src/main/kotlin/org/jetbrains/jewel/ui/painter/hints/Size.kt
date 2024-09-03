package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint
import org.jetbrains.jewel.ui.painter.PainterWrapperHint
import org.jetbrains.jewel.ui.painter.ResizedPainter
import org.jetbrains.jewel.ui.painter.SvgPainterHint

@Immutable
@GenerateDataFunctions
private class SizeImpl(private val width: Int, private val height: Int) :
    PainterSuffixHint(), PainterWrapperHint, SvgPainterHint {
    override fun PainterProviderScope.suffix(): String = buildString {
        append("@")
        append(width)
        append("x")
        append(height)
    }

    override fun PainterProviderScope.wrap(painter: Painter): Painter {
        if (path.contains(suffix())) return painter

        return ResizedPainter(painter, androidx.compose.ui.geometry.Size(width.dp.toPx(), height.dp.toPx()))
    }
}

/**
 * Selects a size variant for the image. If the specific size that was requested is not available, the base image will
 * be used.
 *
 * Note that combining a [Size] with [HiDpi] could lead to unexpected results and is not supported as of now. Generally
 * speaking, however, the IntelliJ Platform tends to use only [Size] for SVGs and only [HiDpi] for PNGs, even though
 * both are in theory supported for all formats.
 *
 * | Base image name    | Sized image name         |
 * |--------------------|--------------------------|
 * | `my-icon.svg`      | `my-icon@20x20.svg`      |
 * | `my-icon_dark.png` | `my-icon@14x14_dark.png` |
 *
 * @see HiDpi
 */
@Suppress("FunctionName")
public fun Size(size: Int): PainterHint {
    require(size > 0) { "Size must be positive" }
    return SizeImpl(size, size)
}

/**
 * Selects a size variant for the image. If the specific size that was requested is not available, the base image will
 * be used.
 *
 * Note that combining a [Size] with [HiDpi] could lead to unexpected results and is not supported as of now. Generally
 * speaking, however, the IntelliJ Platform tends to use only [Size] for SVGs and only [HiDpi] for PNGs, even though
 * both are in theory supported for all formats.
 *
 * | Base image name    | Sized image name         |
 * |--------------------|--------------------------|
 * | `my-icon.svg`      | `my-icon@20x20.svg`      |
 * | `my-icon_dark.png` | `my-icon@14x14_dark.png` |
 *
 * @see HiDpi
 */
@Suppress("FunctionName")
public fun Size(width: Int, height: Int): PainterHint {
    require(width > 0 && height > 0) { "Width and height must be positive" }
    return SizeImpl(width, height)
}
