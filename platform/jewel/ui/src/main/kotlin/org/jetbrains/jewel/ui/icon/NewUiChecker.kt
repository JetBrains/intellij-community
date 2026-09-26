package org.jetbrains.jewel.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** Functional interface that determines whether the IDE is running with the New UI enabled. */
public fun interface NewUiChecker {
    /** Returns `true` if the IDE is currently using the New UI. */
    public fun isNewUi(): Boolean
}

/** CompositionLocal that provides the current [NewUiChecker] instance. */
public val LocalNewUiChecker: ProvidableCompositionLocal<NewUiChecker> = staticCompositionLocalOf {
    error("No NewUiChecker provided")
}

/** The [NewUiChecker] for the current [JewelTheme]. */
public val JewelTheme.Companion.newUiChecker: NewUiChecker
    @Composable get() = LocalNewUiChecker.current
