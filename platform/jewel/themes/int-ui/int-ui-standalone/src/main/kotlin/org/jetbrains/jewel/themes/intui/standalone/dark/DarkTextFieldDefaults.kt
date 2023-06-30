package org.jetbrains.jewel.themes.intui.standalone.dark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.TextFieldColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.textFieldColors
import org.jetbrains.jewel.themes.intui.standalone.IntUiTextFieldDefaults
import org.jetbrains.jewel.themes.intui.standalone.LocalIntUiPalette

object DarkTextFieldDefaults : IntUiTextFieldDefaults() {

    @Composable
    override fun colors(): TextFieldColors {
        val palette = LocalIntUiPalette.current

        return remember(palette) {
            val cursorBrush = SolidColor(palette.grey(12))
            textFieldColors(
                foreground = palette.grey(12),
                background = palette.grey(2),
                cursorBrush = cursorBrush,
                borderStroke = Stroke(1.dp, palette.grey(5), Stroke.Alignment.Inside),
                focusedForeground = palette.grey(12),
                focusedBackground = palette.grey(2),
                focusedCursorBrush = cursorBrush,
                focusedBorderStroke = Stroke(2.dp, palette.blue(6), Stroke.Alignment.Center),
                errorForeground = palette.grey(12),
                errorBackground = palette.grey(2),
                errorCursorBrush = cursorBrush,
                errorBorderStroke = Stroke(2.dp, palette.red(2), Stroke.Alignment.Center),
                errorFocusedForeground = palette.grey(12),
                errorFocusedBackground = palette.grey(2),
                errorFocusedCursorBrush = cursorBrush,
                errorFocusedBorderStroke = Stroke(2.dp, palette.red(6), Stroke.Alignment.Center),
                disabledForeground = palette.grey(7),
                disabledBackground = palette.grey(2),
                disabledBorderStroke = Stroke(1.dp, palette.grey(5), Stroke.Alignment.Center),
                placeholderForeground = palette.grey(7)
            )
        }
    }
}
