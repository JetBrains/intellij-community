package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.jewel.LabelledTextFieldColors
import org.jetbrains.jewel.labelledTextFieldColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiLabelledTextFieldDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object LightLabelledTextFieldDefaults : IntUiLabelledTextFieldDefaults() {

    @Composable
    override fun colors(): LabelledTextFieldColors {
        val baseColors = LightTextFieldDefaults.colors()
        val palette = LocalIntUiPalette.current

        return remember(baseColors, palette) {
            labelledTextFieldColors(
                baseColors,
                labelTextColor = palette.grey(1),
                hintTextColor = palette.grey(6)
            )
        }
    }
}
