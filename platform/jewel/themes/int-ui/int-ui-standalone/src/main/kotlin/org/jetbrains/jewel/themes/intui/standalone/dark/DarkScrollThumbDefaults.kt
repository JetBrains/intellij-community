package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ScrollThumbColors
import org.jetbrains.jewel.scrollThumbColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiScrollThumbDefaults

object DarkScrollThumbDefaults : IntUiScrollThumbDefaults() {
    @Composable
    override fun colors(): ScrollThumbColors {
        return scrollThumbColors(
            Color(0xFF48494B),
            Color(0xFF595A5C)
        )
    }
}
