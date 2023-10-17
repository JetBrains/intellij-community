package org.jetbrains.jewel.painter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.painter.hints.Dark

/**
 * Provides [hints][PainterHint] to a [PainterProvider].
 *
 * @see DarkPainterHintsProvider
 * @see org.jetbrains.jewel.intui.core.IntUiPainterHintsProvider
 * @see org.jetbrains.jewel.intui.standalone.StandalonePainterHintsProvider
 */
@Immutable
interface PainterHintsProvider {

    @Composable
    fun hints(path: String): List<PainterHint>
}

/**
 * The default [PainterHintsProvider] to load dark theme icon variants.
 * It will provide the [Dark] hint when [LocalIsDarkTheme][org.jetbrains.jewel.LocalIsDarkTheme] is true.
 */
object DarkPainterHintsProvider : PainterHintsProvider {

    @Composable
    override fun hints(path: String): List<PainterHint> = listOf(Dark(IntelliJTheme.isDark))
}

val LocalPainterHintsProvider = staticCompositionLocalOf<PainterHintsProvider> {
    DarkPainterHintsProvider
}
