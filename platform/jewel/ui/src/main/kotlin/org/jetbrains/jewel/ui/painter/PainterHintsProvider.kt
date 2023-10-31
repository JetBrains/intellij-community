package org.jetbrains.jewel.ui.painter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.painter.hints.Dark
import org.jetbrains.jewel.ui.painter.hints.HiDpi

/**
 * Provides [hints][PainterHint] to a [PainterProvider].
 *
 * @see CommonPainterHintsProvider
 * @see org.jetbrains.jewel.intui.core.IntUiPainterHintsProvider
 * @see org.jetbrains.jewel.intui.standalone.StandalonePainterHintsProvider
 */
@Immutable
interface PainterHintsProvider {

    /**
     * These hints will be consumed before the hints which are passed as a parameter to [PainterProvider.getPainter].
     */
    @Composable
    fun priorityHints(path: String): List<PainterHint> = emptyList()

    /**
     * These hints will be consumed after the hints which are passed as a parameter to [PainterProvider.getPainter].
     */
    @Composable
    fun hints(path: String): List<PainterHint>
}

/**
 * The default [PainterHintsProvider] to load dark theme icon variants.
 * It will provide the [Dark] hint when [LocalIsDarkTheme][org.jetbrains.jewel.LocalIsDarkTheme] is true.
 */
object CommonPainterHintsProvider : PainterHintsProvider {

    @Composable
    override fun hints(path: String): List<PainterHint> = listOf(
        HiDpi(),
        Dark(JewelTheme.isDark),
    )
}

val LocalPainterHintsProvider = staticCompositionLocalOf<PainterHintsProvider> {
    CommonPainterHintsProvider
}
