package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.jewel.ProgressBarColors
import org.jetbrains.jewel.progressBarDefaultsColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiProgressBarDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkProgressBarDefaults : IntUiProgressBarDefaults() {

    @Composable
    override fun colors(): ProgressBarColors {
        val palette = LocalIntUiPalette.current
        return remember {
            progressBarDefaultsColors(
                indeterminateStartColor = palette.blue(9),
                indeterminateEndColor = palette.blue(5),
                determinateProgressColor = palette.blue(7),
                trackColor = palette.grey(4)
            )
        }
    }
}
