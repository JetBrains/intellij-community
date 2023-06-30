package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.LinkColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.linkColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiLinkDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkLinkDefaults : IntUiLinkDefaults() {

    @Composable
    override fun colors(): LinkColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            linkColors(
                textColor = palette.blue(9),
                visitedTextColor = palette.blue(9),
                disabledTextColor = palette.grey(7),
                iconColor = palette.grey(8),
                disabledIconColor = palette.grey(7),
                haloStroke = Stroke(1.dp, palette.blue(6), Stroke.Alignment.Outside)
            )
        }
    }
}
