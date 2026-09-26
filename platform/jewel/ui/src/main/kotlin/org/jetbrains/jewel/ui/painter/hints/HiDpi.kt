package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint

@Immutable
private object HiDpiImpl : PainterSuffixHint() {
    override fun PainterProviderScope.suffix(): String = "@2x"

    override fun PainterProviderScope.canApply(): Boolean = density > 1f

    override fun toString(): String = "HiDpi"
}

/**
 * Selects the `@2x` HiDPI variant for bitmap images when running on a HiDPI screen.
 *
 * If an image does not have a HiDPI variant, the base image will be used.
 *
 * Note that combining a [Size] with [HiDpi] could lead to unexpected results and is not supported as of now. Generally
 * speaking, however, the IntelliJ Platform tends to use only [Size] for SVGs and only [HiDpi] for PNGs, even though
 * both are in theory supported for all formats.
 *
 * | Base image name | HiDPI image name |
 * |-----------------|------------------|
 * | `my-icon.png`   | `my-icon@2x.png` |
 * | `my-icon.svg`   | N/A              |
 *
 * @see Size
 */
@Suppress("FunctionName") public fun HiDpi(): PainterHint = HiDpiImpl
