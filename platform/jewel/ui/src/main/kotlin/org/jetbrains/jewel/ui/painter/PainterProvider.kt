package org.jetbrains.jewel.ui.painter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.painter.Painter

/**
 * Provides a [Painter] for an image, which may be transformed by the provided hints.
 *
 * Note: implementations of this interface should handle the passed [PainterHint]s correctly. For now, this means
 * calling [PainterPathHint.patch] and [PainterSvgPatchHint.patch]. Most likely, a [PainterProvider] should also hold
 * the resource path and [ClassLoader] references.
 */
public interface PainterProvider {
    /**
     * Provides a [Painter] using the specified [PainterHint]s. The painters are
     * [remember][androidx.compose.runtime.remember]ed and this function can be called multiple times for the same data.
     *
     * Depending on the implementation, errors may be suppressed and a replacement painter provided.
     */
    @Composable public fun getPainter(vararg hints: PainterHint): State<Painter>
}
