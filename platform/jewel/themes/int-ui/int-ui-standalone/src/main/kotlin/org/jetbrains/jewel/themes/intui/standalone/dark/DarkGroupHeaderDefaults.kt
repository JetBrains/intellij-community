package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.jewel.GroupHeaderColors
import org.jetbrains.jewel.groupHeaderColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiGroupHeaderDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkGroupHeaderDefaults : IntUiGroupHeaderDefaults() {

    @Composable
    override fun colors(): GroupHeaderColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            groupHeaderColors(
                textColor = palette.grey(12),
                dividerColor = palette.grey(3)
            )
        }
    }
}
