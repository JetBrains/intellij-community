package org.jetbrains.jewel.painter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.painter.Painter

/**
 * Implementations of this interface should handle the passed [PainterHint]s correctly.
 * For now, this means calling [PainterPathHint.patch] and [PainterSvgPatchHint.patch].
 * Most likely, a [PainterProvider] should also hold the resource path and [ClassLoader]
 * references.
 */
@Immutable
interface PainterProvider {

    /**
     * Provides a [Painter] using the specified [PainterHint]s.
     */
    @Composable
    fun getPainter(vararg hints: PainterHint): State<Painter>
}
