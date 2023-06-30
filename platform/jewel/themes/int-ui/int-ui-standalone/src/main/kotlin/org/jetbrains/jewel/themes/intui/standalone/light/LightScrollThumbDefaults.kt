package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ScrollThumbColors
import org.jetbrains.jewel.scrollThumbColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiScrollThumbDefaults

object LightScrollThumbDefaults : IntUiScrollThumbDefaults() {

    @Composable
    override fun colors(): ScrollThumbColors =
        remember {
            scrollThumbColors(
                Color(0xFFD9D9D9),
                Color(0xFF7B7C7D)
            )
        }
}
