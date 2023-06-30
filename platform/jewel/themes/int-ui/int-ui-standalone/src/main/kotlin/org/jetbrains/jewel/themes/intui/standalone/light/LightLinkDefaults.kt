package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.LinkColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.linkColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiLinkDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object LightLinkDefaults : IntUiLinkDefaults() {

    @Composable
    override fun colors(): LinkColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            linkColors(
                textColor = palette.blue(2),
                visitedTextColor = palette.blue(2),
                disabledTextColor = palette.grey(8),
                iconColor = palette.grey(7),
                disabledIconColor = palette.grey(8),
                haloStroke = Stroke(1.dp, palette.blue(4), Stroke.Alignment.Outside)
            )
        }
    }
}
