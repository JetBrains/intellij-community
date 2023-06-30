package org.jetbrains.jewel.themes.intui.standalone.light

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.TextFieldColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.textFieldColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiTextFieldDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object LightTextFieldDefaults : IntUiTextFieldDefaults() {

    @Composable
    override fun colors(): TextFieldColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            val cursorBrush = SolidColor(palette.grey(1))
            textFieldColors(
                foreground = palette.grey(1),
                background = palette.grey(14),
                cursorBrush = cursorBrush,
                borderStroke = Stroke(1.dp, palette.grey(9), Stroke.Alignment.Inside),
                focusedForeground = palette.grey(1),
                focusedBackground = palette.grey(14),
                focusedCursorBrush = cursorBrush,
                focusedBorderStroke = Stroke(2.dp, palette.blue(4), Stroke.Alignment.Center),
                errorForeground = palette.grey(1),
                errorBackground = palette.grey(14),
                errorCursorBrush = cursorBrush,
                errorBorderStroke = Stroke(2.dp, palette.red(9), Stroke.Alignment.Center),
                errorFocusedForeground = palette.grey(1),
                errorFocusedBackground = palette.grey(14),
                errorFocusedCursorBrush = cursorBrush,
                errorFocusedBorderStroke = Stroke(2.dp, palette.red(4), Stroke.Alignment.Center),
                disabledForeground = palette.grey(8),
                disabledBackground = palette.grey(13),
                disabledBorderStroke = Stroke(1.dp, palette.grey(11), Stroke.Alignment.Center),
                placeholderForeground = palette.grey(8)
            )
        }
    }
}
