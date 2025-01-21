package org.jetbrains.jewel.bridge

import com.intellij.ide.ui.UITheme
import org.jetbrains.jewel.foundation.theme.ThemeIconData

@Suppress("UnstableApiUsage")
public fun ThemeIconData.Companion.readFromLaF(): ThemeIconData {
    val uiTheme = currentUiThemeOrNull()
    val bean = uiTheme?.describe()
    val iconMap = bean?.icons.orEmpty()
    val selectedIconColorPalette = bean?.iconColorsOnSelection.orEmpty()

    val colorPalette = UITheme.getColorPalette()
    return ThemeIconData(iconMap, colorPalette, selectedIconColorPalette)
}
