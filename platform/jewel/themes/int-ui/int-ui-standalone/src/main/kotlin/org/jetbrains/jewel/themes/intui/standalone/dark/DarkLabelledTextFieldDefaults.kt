package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.jewel.LabelledTextFieldColors
import org.jetbrains.jewel.labelledTextFieldColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiLabelledTextFieldDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkLabelledTextFieldDefaults : IntUiLabelledTextFieldDefaults() {

    @Composable
    override fun colors(): LabelledTextFieldColors {
        val baseColors = DarkTextFieldDefaults.colors()
        val palette = LocalIntUiPalette.current

        return remember(baseColors, palette) {
            labelledTextFieldColors(
                baseColors,
                labelTextColor = palette.grey(12),
                hintTextColor = palette.grey(7)
            )
        }
    }
}
