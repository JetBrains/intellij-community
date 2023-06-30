package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.jewel.ProgressBarColors
import org.jetbrains.jewel.progressBarDefaultsColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiProgressBarDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object LightProgressBarDefaults : IntUiProgressBarDefaults() {

    @Composable
    override fun colors(): ProgressBarColors {
        val palette = LocalIntUiPalette.current
        return remember {
            progressBarDefaultsColors(
                indeterminateStartColor = palette.blue(9),
                indeterminateEndColor = palette.blue(4),
                determinateProgressColor = palette.blue(4),
                trackColor = palette.grey(11)
            )
        }
    }
}
