package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.MenuColors
import org.jetbrains.jewel.MenuItemColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.themes.intui.standalone.IntUiMenuDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkMenuDefaults : IntUiMenuDefaults() {

    @Composable
    override fun menuColors(): MenuColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            org.jetbrains.jewel.menuColors(
                background = palette.grey(2),
                borderStroke = Stroke(1.dp, palette.grey(3), Stroke.Alignment.Inside),
                shadowColor = Color(0x66000000)
            )
        }
    }

    @Composable
    override fun menuItemColors(): MenuItemColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            org.jetbrains.jewel.menuItemColors(
                foreground = palette.grey(12),
                background = palette.grey(2),
                iconColor = palette.grey(10),
                focusedForeground = palette.grey(12),
                focusedBackground = palette.blue(2),
                focusedIconColor = palette.grey(10),
                disabledForeground = palette.grey(7),
                disabledBackground = palette.grey(2),
                disabledIconColor = palette.grey(10),
                separatorColor = palette.grey(3)
            )
        }
    }
}
