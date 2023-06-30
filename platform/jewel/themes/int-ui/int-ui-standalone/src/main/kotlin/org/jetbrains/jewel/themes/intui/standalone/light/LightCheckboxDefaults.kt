package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.CheckboxColors
import org.jetbrains.jewel.checkBoxColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.themes.intui.standalone.IntUiCheckboxDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object LightCheckboxDefaults : IntUiCheckboxDefaults() {

    @Composable
    override fun colors(): CheckboxColors {
        val palette = LocalIntUiPalette.current
        return remember(palette) {
            checkBoxColors(
                checkmarkColor = palette.grey(14),
                textColor = palette.grey(1),
                uncheckedBackground = palette.grey(14),
                uncheckedStroke = Stroke(1.dp, palette.grey(8), Stroke.Alignment.Inside),
                uncheckedFocusedStroke = Stroke.None,
                uncheckedFocusHoloStroke = Stroke(2.dp, palette.blue(4), Stroke.Alignment.Center),
                uncheckedErrorHoloStroke = Stroke(2.dp, palette.red(4), Stroke.Alignment.Center),
                uncheckedHoveredBackground = palette.grey(14),
                uncheckedHoveredStroke = Stroke(1.dp, palette.grey(6), Stroke.Alignment.Inside),
                uncheckedDisabledBackground = palette.grey(13),
                uncheckedDisabledStroke = Stroke(1.dp, palette.grey(11), Stroke.Alignment.Inside),
                checkedBackground = palette.blue(4),
                checkedStroke = Stroke.None,
                checkedFocusedStroke = Stroke(1.dp, palette.grey(14), Stroke.Alignment.Outside),
                checkedFocusHoloStroke = Stroke(3.dp, palette.blue(4), Stroke.Alignment.Outside),
                checkedErrorHoloStroke = Stroke(3.dp, palette.red(4), Stroke.Alignment.Outside),
                checkedHoveredBackground = palette.blue(3),
                checkedHoveredStroke = Stroke.None,
                checkedDisabledBackground = palette.grey(9),
                checkedDisabledStroke = Stroke.None,
                disabledCheckmarkColor = palette.grey(14),
                disabledTextColor = palette.grey(8)
            )
        }
    }
}
