package org.jetbrains.jewel.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.jewel.foundation.theme.JewelTheme

public fun interface NewUiChecker {
    public fun isNewUi(): Boolean
}

public val LocalNewUiChecker: ProvidableCompositionLocal<NewUiChecker> = staticCompositionLocalOf {
    error("No NewUiChecker provided")
}

public val JewelTheme.Companion.newUiChecker: NewUiChecker
    @Composable get() = LocalNewUiChecker.current
