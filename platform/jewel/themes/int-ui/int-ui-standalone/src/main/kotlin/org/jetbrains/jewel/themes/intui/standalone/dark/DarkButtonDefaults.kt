package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ButtonColors
import org.jetbrains.jewel.buttonColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.themes.intui.standalone.IntUiButtonDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkButtonDefaults : IntUiButtonDefaults() {

    @Composable
    override fun primaryButtonColors(): ButtonColors {
        val palette = LocalIntUiPalette.current
        return remember(palette) {
            buttonColors(
                backgroundBrush = SolidColor(palette.blue(6)),
                contentColor = palette.grey(14),
                borderStroke = Stroke.None,
                disabledBackgroundBrush = SolidColor(palette.grey(5)),
                disabledContentColor = palette.grey(8),
                disabledBorderStroke = Stroke.None,
                hoveredBackgroundBrush = SolidColor(palette.blue(5)),
                hoveredContentColor = palette.grey(14),
                hoveredBorderStroke = Stroke.None,
                pressedBackgroundBrush = SolidColor(palette.blue(4)),
                pressedContentColor = palette.grey(14),
                pressedBorderStroke = Stroke.None,
                focusedBackgroundBrush = SolidColor(palette.blue(6)),
                focusedContentColor = palette.grey(14),
                focusedBorderStroke = Stroke(1.dp, palette.grey(2), Stroke.Alignment.Outside),
                focusHaloStroke = Stroke(3.dp, palette.blue(6), Stroke.Alignment.Outside)
            )
        }
    }

    @Composable
    override fun outlinedButtonColors(): ButtonColors {
        val palette = LocalIntUiPalette.current
        return remember(palette) {
            buttonColors(
                backgroundBrush = SolidColor(palette.grey(2)),
                contentColor = palette.grey(12),
                borderStroke = Stroke(1.dp, palette.grey(5), Stroke.Alignment.Inside),
                disabledBackgroundBrush = SolidColor(palette.grey(5)),
                disabledContentColor = palette.grey(8),
                disabledBorderStroke = Stroke.None,
                hoveredBackgroundBrush = SolidColor(palette.grey(2)),
                hoveredContentColor = palette.grey(12),
                hoveredBorderStroke = Stroke(1.dp, palette.grey(7), Stroke.Alignment.Inside),
                pressedBackgroundBrush = SolidColor(palette.grey(2)),
                pressedContentColor = palette.grey(12),
                pressedBorderStroke = Stroke(1.dp, palette.grey(7), Stroke.Alignment.Inside),
                focusedBackgroundBrush = SolidColor(palette.grey(2)),
                focusedContentColor = palette.grey(12),
                focusedBorderStroke = Stroke.None,
                focusHaloStroke = Stroke(2.dp, palette.blue(6), Stroke.Alignment.Center)
            )
        }
    }
}
