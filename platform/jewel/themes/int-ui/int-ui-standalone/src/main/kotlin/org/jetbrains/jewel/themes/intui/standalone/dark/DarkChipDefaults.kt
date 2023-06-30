package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ChipColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.themes.intui.standalone.IntUiChipDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkChipDefaults : IntUiChipDefaults() {

    @Composable
    override fun chipColors(): ChipColors {
        val palette = LocalIntUiPalette.current
        return remember(palette) {
            org.jetbrains.jewel.chipColors(
                backgroundBrush = SolidColor(palette.grey(2)),
                contentColor = palette.grey(12),
                borderStroke = Stroke(1.dp, palette.grey(5), Stroke.Alignment.Inside),
                focusedBackground = SolidColor(palette.grey(2)),
                focusedContentColor = palette.grey(12),
                focusedBorderStroke = Stroke.None,
                focusedHaloStroke = Stroke(2.dp, palette.blue(6), Stroke.Alignment.Center),
                hoveredBackground = SolidColor(palette.grey(2)),
                hoveredContentColor = palette.grey(1),
                hoveredBorderStroke = Stroke(1.dp, palette.grey(7), Stroke.Alignment.Inside),
                pressedBackground = SolidColor(palette.grey(2)),
                pressedContentColor = palette.grey(12),
                pressedBorderStroke = Stroke(1.dp, palette.grey(7), Stroke.Alignment.Inside),
                disabledBackground = SolidColor(palette.grey(5)),
                disabledContentColor = palette.grey(8),
                disabledBorderStroke = Stroke(1.dp, palette.grey(6), Stroke.Alignment.Inside)
            )
        }
    }
}
