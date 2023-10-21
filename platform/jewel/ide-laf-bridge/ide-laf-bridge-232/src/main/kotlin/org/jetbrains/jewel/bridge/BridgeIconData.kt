package org.jetbrains.jewel.bridge

import com.intellij.ide.ui.UITheme
import com.intellij.ui.ColorUtil
import org.jetbrains.jewel.InternalJewelApi
import org.jetbrains.jewel.ThemeIconData

@OptIn(InternalJewelApi::class)
fun ThemeIconData.Companion.readFromLaF(): ThemeIconData {
    val uiTheme = currentUiThemeOrNull()
    val iconMap = uiTheme?.icons.orEmpty()
    val selectedIconColorPalette = uiTheme?.selectedIconColorPalette.orEmpty().mapValues {
        ColorUtil.fromHex(it.value).rgb
    }

    val colorPalette = UITheme.getColorPalette()
    return ThemeIconData(iconMap, colorPalette, selectedIconColorPalette)
}
