package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.jewel.TreeColors
import org.jetbrains.jewel.themes.intui.standalone.IntUITreeDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette
import org.jetbrains.jewel.treeColors

object DarkTreeDefaults : IntUITreeDefaults() {

    @Composable
    override fun colors(): TreeColors {
        val palette = LocalIntUiPalette.current
        return remember(this) {
            treeColors(
                backgroundColor = palette.grey(2),
                foregroundColor = palette.grey(2),
                selectedBackgroundColor = palette.grey(8),
                selectedForegroundColor = palette.grey(2),
                focusedBackgroundColor = palette.blue(9),
                focusedForegroundColor = palette.grey(2),
                selectedFocusedBackgroundColor = palette.blue(6),
                selectedFocusedForegroundColor = palette.grey(2),
                nodeIconColor = palette.grey(14)
            )
        }
    }
}
