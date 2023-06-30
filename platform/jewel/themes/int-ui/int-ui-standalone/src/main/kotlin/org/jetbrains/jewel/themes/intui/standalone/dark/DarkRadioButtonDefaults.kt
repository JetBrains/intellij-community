package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.RadioButtonColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.radioButtonColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiRadioButtonDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkRadioButtonDefaults : IntUiRadioButtonDefaults() {

    @Composable
    override fun colors(): RadioButtonColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            radioButtonColors(
                checkmarkColor = palette.grey(14),
                textColor = palette.grey(12),
                unselectedBackground = palette.grey(2),
                unselectedStroke = Stroke(1.dp, palette.grey(6), Stroke.Alignment.Inside),
                unselectedFocusedStroke = Stroke.None,
                unselectedFocusHoloStroke = Stroke(2.dp, palette.blue(6), Stroke.Alignment.Center),
                unselectedErrorHoloStroke = Stroke(2.dp, palette.red(6), Stroke.Alignment.Center),
                unselectedHoveredBackground = palette.grey(2),
                unselectedHoveredStroke = Stroke(1.dp, palette.grey(9), Stroke.Alignment.Inside),
                unselectedDisabledBackground = palette.grey(3),
                unselectedDisabledStroke = Stroke(1.dp, palette.grey(6), Stroke.Alignment.Inside),
                selectedBackground = palette.blue(6),
                selectedStroke = Stroke.None,
                selectedFocusedStroke = Stroke(1.dp, palette.grey(2), Stroke.Alignment.Outside),
                selectedFocusHoloStroke = Stroke(3.dp, palette.blue(6), Stroke.Alignment.Outside),
                selectedErrorHoloStroke = Stroke(3.dp, palette.red(6), Stroke.Alignment.Outside),
                selectedHoveredBackground = palette.blue(5),
                selectedHoveredStroke = Stroke.None,
                selectedDisabledBackground = palette.grey(3),
                selectedDisabledStroke = Stroke.None,
                disabledCheckmarkColor = palette.grey(8),
                disabledTextColor = palette.grey(7)
            )
        }
    }
}
