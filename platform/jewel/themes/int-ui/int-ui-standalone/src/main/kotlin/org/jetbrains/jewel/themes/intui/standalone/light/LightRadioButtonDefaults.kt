package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.RadioButtonColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.radioButtonColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiRadioButtonDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object LightRadioButtonDefaults : IntUiRadioButtonDefaults() {

    @Composable
    override fun colors(): RadioButtonColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            radioButtonColors(
                checkmarkColor = palette.grey(14),
                textColor = palette.grey(1),
                unselectedBackground = palette.grey(14),
                unselectedStroke = Stroke(1.dp, palette.grey(8), Stroke.Alignment.Inside),
                unselectedFocusedStroke = Stroke.None,
                unselectedFocusHoloStroke = Stroke(2.dp, palette.blue(4), Stroke.Alignment.Center),
                unselectedErrorHoloStroke = Stroke(2.dp, palette.red(4), Stroke.Alignment.Center),
                unselectedHoveredBackground = palette.grey(14),
                unselectedHoveredStroke = Stroke(1.dp, palette.grey(6), Stroke.Alignment.Inside),
                unselectedDisabledBackground = palette.grey(13),
                unselectedDisabledStroke = Stroke(1.dp, palette.grey(11), Stroke.Alignment.Inside),
                selectedBackground = palette.blue(4),
                selectedStroke = Stroke.None,
                selectedFocusedStroke = Stroke(1.dp, palette.grey(14), Stroke.Alignment.Outside),
                selectedFocusHoloStroke = Stroke(3.dp, palette.blue(4), Stroke.Alignment.Outside),
                selectedErrorHoloStroke = Stroke(3.dp, palette.red(4), Stroke.Alignment.Outside),
                selectedHoveredBackground = palette.blue(3),
                selectedHoveredStroke = Stroke.None,
                selectedDisabledBackground = palette.grey(9),
                selectedDisabledStroke = Stroke.None,
                disabledCheckmarkColor = palette.grey(14),
                disabledTextColor = palette.grey(8)
            )
        }
    }
}
