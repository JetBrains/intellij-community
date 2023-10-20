package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.styling.LabelledTextFieldColors
import org.jetbrains.jewel.styling.LabelledTextFieldMetrics
import org.jetbrains.jewel.styling.LabelledTextFieldStyle
import org.jetbrains.jewel.styling.LabelledTextFieldTextStyles

@Composable
fun LabelledTextFieldStyle.Companion.light(
    colors: LabelledTextFieldColors = LabelledTextFieldColors.light(),
    metrics: LabelledTextFieldMetrics = LabelledTextFieldMetrics.defaults(),
    textStyle: TextStyle = IntUiTheme.defaultTextStyle,
    textStyles: LabelledTextFieldTextStyles = LabelledTextFieldTextStyles.light(),
) = LabelledTextFieldStyle(colors, metrics, textStyle, textStyles)

@Composable
fun LabelledTextFieldStyle.Companion.dark(
    colors: LabelledTextFieldColors = LabelledTextFieldColors.dark(),
    metrics: LabelledTextFieldMetrics = LabelledTextFieldMetrics.defaults(),
    textStyle: TextStyle = IntUiTheme.defaultTextStyle,
    textStyles: LabelledTextFieldTextStyles = LabelledTextFieldTextStyles.dark(),
) = LabelledTextFieldStyle(colors, metrics, textStyle, textStyles)

@Composable
fun LabelledTextFieldColors.Companion.light(
    background: Color = IntUiLightTheme.colors.grey(14),
    backgroundDisabled: Color = IntUiLightTheme.colors.grey(13),
    backgroundFocused: Color = background,
    backgroundPressed: Color = background,
    backgroundHovered: Color = background,
    content: Color = IntUiLightTheme.colors.grey(1),
    contentDisabled: Color = IntUiLightTheme.colors.grey(8),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    border: Color = IntUiLightTheme.colors.grey(9),
    borderDisabled: Color = IntUiLightTheme.colors.grey(11),
    borderFocused: Color = IntUiLightTheme.colors.blue(4),
    borderPressed: Color = border,
    borderHovered: Color = border,
    caret: Color = IntUiLightTheme.colors.grey(1),
    caretDisabled: Color = caret,
    caretFocused: Color = caret,
    caretPressed: Color = caret,
    caretHovered: Color = caret,
    placeholder: Color = IntUiLightTheme.colors.grey(8),
    label: Color = Color.Unspecified,
    hint: Color = IntUiLightTheme.colors.grey(6),
) = LabelledTextFieldColors(
    background,
    backgroundDisabled,
    backgroundFocused,
    backgroundPressed,
    backgroundHovered,
    content,
    contentDisabled,
    contentFocused,
    contentPressed,
    contentHovered,
    border,
    borderDisabled,
    borderFocused,
    borderPressed,
    borderHovered,
    caret,
    caretDisabled,
    caretFocused,
    caretPressed,
    caretHovered,
    placeholder,
    label,
    hint,
)

@Composable
fun LabelledTextFieldColors.Companion.dark(
    background: Color = IntUiDarkTheme.colors.grey(2),
    backgroundDisabled: Color = background,
    backgroundFocused: Color = background,
    backgroundPressed: Color = background,
    backgroundHovered: Color = background,
    content: Color = IntUiDarkTheme.colors.grey(12),
    contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    border: Color = IntUiDarkTheme.colors.grey(5),
    borderDisabled: Color = border,
    borderFocused: Color = IntUiDarkTheme.colors.blue(6),
    borderPressed: Color = border,
    borderHovered: Color = border,
    caret: Color = IntUiDarkTheme.colors.grey(12),
    caretDisabled: Color = caret,
    caretFocused: Color = caret,
    caretPressed: Color = caret,
    caretHovered: Color = caret,
    placeholder: Color = IntUiDarkTheme.colors.grey(7),
    label: Color = Color.Unspecified,
    hint: Color = IntUiDarkTheme.colors.grey(7),
) = LabelledTextFieldColors(
    background,
    backgroundDisabled,
    backgroundFocused,
    backgroundPressed,
    backgroundHovered,
    content,
    contentDisabled,
    contentFocused,
    contentPressed,
    contentHovered,
    border,
    borderDisabled,
    borderFocused,
    borderPressed,
    borderHovered,
    caret,
    caretDisabled,
    caretFocused,
    caretPressed,
    caretHovered,
    placeholder,
    label,
    hint,
)

fun LabelledTextFieldMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
    minSize: DpSize = DpSize(49.dp, 24.dp),
    borderWidth: Dp = 1.dp,
    labelSpacing: Dp = 6.dp,
    hintSpacing: Dp = 6.dp,
) = LabelledTextFieldMetrics(borderWidth, contentPadding, cornerSize, minSize, labelSpacing, hintSpacing)

fun LabelledTextFieldTextStyles.Companion.light(
    label: TextStyle = IntUiTheme.defaultTextStyle,
    hint: TextStyle = IntUiTheme.defaultTextStyle.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
) = LabelledTextFieldTextStyles(label, hint)

fun LabelledTextFieldTextStyles.Companion.dark(
    label: TextStyle = IntUiTheme.defaultTextStyle,
    hint: TextStyle = IntUiTheme.defaultTextStyle.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
) = LabelledTextFieldTextStyles(label, hint)
