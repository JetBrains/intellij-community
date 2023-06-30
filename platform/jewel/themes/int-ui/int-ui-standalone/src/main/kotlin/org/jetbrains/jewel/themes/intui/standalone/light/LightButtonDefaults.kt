package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ButtonColors
import org.jetbrains.jewel.buttonColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.themes.intui.standalone.IntUiButtonDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object LightButtonDefaults : IntUiButtonDefaults() {

    @Composable
    override fun primaryButtonColors(): ButtonColors {
        val palette = LocalIntUiPalette.current
        return remember(palette) {
            buttonColors(
                backgroundBrush = SolidColor(palette.blue(4)),
                contentColor = palette.grey(14),
                borderStroke = Stroke.None,
                disabledBackgroundBrush = SolidColor(palette.grey(12)),
                disabledContentColor = palette.grey(8),
                disabledBorderStroke = Stroke.None,
                hoveredBackgroundBrush = SolidColor(palette.blue(3)),
                hoveredContentColor = palette.grey(14),
                hoveredBorderStroke = Stroke.None,
                pressedBackgroundBrush = SolidColor(palette.blue(2)),
                pressedContentColor = palette.grey(14),
                pressedBorderStroke = Stroke.None,
                focusedBackgroundBrush = SolidColor(palette.blue(4)),
                focusedContentColor = palette.grey(14),
                focusedBorderStroke = Stroke(1.dp, palette.grey(14), Stroke.Alignment.Outside),
                focusHaloStroke = Stroke(3.dp, palette.blue(4), Stroke.Alignment.Outside)
            )
        }
    }

    @Composable
    override fun outlinedButtonColors(): ButtonColors {
        val palette = LocalIntUiPalette.current
        return remember(palette) {
            buttonColors(
                backgroundBrush = SolidColor(palette.grey(14)),
                contentColor = palette.grey(1),
                borderStroke = Stroke(1.dp, palette.grey(9), Stroke.Alignment.Inside),
                disabledBackgroundBrush = SolidColor(palette.grey(12)),
                disabledContentColor = palette.grey(8),
                disabledBorderStroke = Stroke.None,
                hoveredBackgroundBrush = SolidColor(palette.grey(14)),
                hoveredContentColor = palette.grey(1),
                hoveredBorderStroke = Stroke(1.dp, palette.grey(8), Stroke.Alignment.Inside),
                pressedBackgroundBrush = SolidColor(palette.grey(13)),
                pressedContentColor = palette.grey(1),
                pressedBorderStroke = Stroke(1.dp, palette.grey(7), Stroke.Alignment.Inside),
                focusedBackgroundBrush = SolidColor(palette.grey(14)),
                focusedContentColor = palette.grey(1),
                focusedBorderStroke = Stroke.None,
                focusHaloStroke = Stroke(2.dp, palette.blue(4), Stroke.Alignment.Center)
            )
        }
    }
}
