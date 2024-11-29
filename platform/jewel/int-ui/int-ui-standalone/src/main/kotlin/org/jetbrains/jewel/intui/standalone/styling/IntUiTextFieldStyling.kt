package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.TextFieldColors
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle

@Composable
public fun TextFieldStyle.Companion.light(
    colors: TextFieldColors = TextFieldColors.light(),
    metrics: TextFieldMetrics = TextFieldMetrics.defaults(),
): TextFieldStyle = TextFieldStyle(colors, metrics)

@Composable
public fun TextFieldStyle.Companion.dark(
    colors: TextFieldColors = TextFieldColors.dark(),
    metrics: TextFieldMetrics = TextFieldMetrics.defaults(),
): TextFieldStyle = TextFieldStyle(colors, metrics)

@Composable
public fun TextFieldColors.Companion.light(
    background: Color = IntUiLightTheme.colors.gray(14),
    backgroundDisabled: Color = Color.Unspecified,
    backgroundFocused: Color = background,
    backgroundPressed: Color = background,
    backgroundHovered: Color = background,
    content: Color = IntUiLightTheme.colors.gray(1),
    contentDisabled: Color = IntUiLightTheme.colors.gray(8),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    border: Color = IntUiLightTheme.colors.gray(9),
    borderDisabled: Color = IntUiLightTheme.colors.gray(11),
    borderFocused: Color = IntUiLightTheme.colors.blue(4),
    borderPressed: Color = border,
    borderHovered: Color = border,
    caret: Color = IntUiLightTheme.colors.gray(1),
    caretDisabled: Color = caret,
    caretFocused: Color = caret,
    caretPressed: Color = caret,
    caretHovered: Color = caret,
    placeholder: Color = IntUiLightTheme.colors.gray(8),
): TextFieldColors =
    TextFieldColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        border = border,
        borderDisabled = borderDisabled,
        borderFocused = borderFocused,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
        caret = caret,
        caretDisabled = caretDisabled,
        caretFocused = caretFocused,
        caretPressed = caretPressed,
        caretHovered = caretHovered,
        placeholder = placeholder,
    )

@Composable
public fun TextFieldColors.Companion.dark(
    background: Color = IntUiDarkTheme.colors.gray(2),
    backgroundDisabled: Color = Color.Unspecified,
    backgroundFocused: Color = background,
    backgroundPressed: Color = background,
    backgroundHovered: Color = background,
    content: Color = IntUiDarkTheme.colors.gray(12),
    contentDisabled: Color = IntUiDarkTheme.colors.gray(7),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    border: Color = IntUiDarkTheme.colors.gray(5),
    borderDisabled: Color = border,
    borderFocused: Color = IntUiDarkTheme.colors.blue(6),
    borderPressed: Color = border,
    borderHovered: Color = border,
    caret: Color = IntUiDarkTheme.colors.gray(12),
    caretDisabled: Color = caret,
    caretFocused: Color = caret,
    caretPressed: Color = caret,
    caretHovered: Color = caret,
    placeholder: Color = IntUiDarkTheme.colors.gray(7),
): TextFieldColors =
    TextFieldColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        border = border,
        borderDisabled = borderDisabled,
        borderFocused = borderFocused,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
        caret = caret,
        caretDisabled = caretDisabled,
        caretFocused = caretFocused,
        caretPressed = caretPressed,
        caretHovered = caretHovered,
        placeholder = placeholder,
    )

public fun TextFieldMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp), // 8 + 1 (border)
    minSize: DpSize = DpSize(144.dp, 28.dp),
    borderWidth: Dp = 1.dp,
): TextFieldMetrics = TextFieldMetrics(borderWidth, contentPadding, cornerSize, minSize)
