package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle

val ButtonStyle.Companion.Default: IntUiDefaultButtonStyleFactory
    get() = IntUiDefaultButtonStyleFactory

object IntUiDefaultButtonStyleFactory {

    @Composable
    fun light(
        colors: ButtonColors = ButtonColors.Default.light(),
        metrics: ButtonMetrics = ButtonMetrics.default(),
    ) = ButtonStyle(colors, metrics)

    @Composable
    fun dark(
        colors: ButtonColors = ButtonColors.Default.dark(),
        metrics: ButtonMetrics = ButtonMetrics.default(),
    ) = ButtonStyle(colors, metrics)
}

val ButtonStyle.Companion.Outlined: IntUiOutlinedButtonStyleFactory
    get() = IntUiOutlinedButtonStyleFactory

object IntUiOutlinedButtonStyleFactory {

    @Composable
    fun light(
        colors: ButtonColors = ButtonColors.Outlined.light(),
        metrics: ButtonMetrics = ButtonMetrics.outlined(),
    ) = ButtonStyle(colors, metrics)

    @Composable
    fun dark(
        colors: ButtonColors = ButtonColors.Outlined.dark(),
        metrics: ButtonMetrics = ButtonMetrics.outlined(),
    ) = ButtonStyle(colors, metrics)
}

val ButtonColors.Companion.Default: IntUiDefaultButtonColorFactory
    get() = IntUiDefaultButtonColorFactory

object IntUiDefaultButtonColorFactory {

    @Composable
    fun light(
        background: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
        backgroundDisabled: Brush = SolidColor(IntUiLightTheme.colors.grey(12)),
        backgroundFocused: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
        backgroundPressed: Brush = SolidColor(IntUiLightTheme.colors.blue(2)),
        backgroundHovered: Brush = SolidColor(IntUiLightTheme.colors.blue(3)),
        content: Color = IntUiLightTheme.colors.grey(14),
        contentDisabled: Color = IntUiLightTheme.colors.grey(8),
        contentFocused: Color = IntUiLightTheme.colors.grey(14),
        contentPressed: Color = IntUiLightTheme.colors.grey(14),
        contentHovered: Color = IntUiLightTheme.colors.grey(14),
        border: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
        borderDisabled: Brush = SolidColor(IntUiLightTheme.colors.grey(11)),
        borderFocused: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
        borderPressed: Brush = border,
        borderHovered: Brush = border,
    ) = ButtonColors(
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
    )

    @Composable
    fun dark(
        background: Brush = SolidColor(IntUiDarkTheme.colors.blue(6)),
        backgroundDisabled: Brush = SolidColor(IntUiDarkTheme.colors.grey(5)),
        backgroundFocused: Brush = SolidColor(IntUiDarkTheme.colors.blue(6)),
        backgroundPressed: Brush = SolidColor(IntUiDarkTheme.colors.blue(4)),
        backgroundHovered: Brush = SolidColor(IntUiDarkTheme.colors.blue(5)),
        content: Color = IntUiDarkTheme.colors.grey(14),
        contentDisabled: Color = IntUiDarkTheme.colors.grey(8),
        contentFocused: Color = IntUiDarkTheme.colors.grey(14),
        contentPressed: Color = IntUiDarkTheme.colors.grey(14),
        contentHovered: Color = IntUiDarkTheme.colors.grey(14),
        border: Brush = SolidColor(IntUiDarkTheme.colors.blue(6)),
        borderDisabled: Brush = SolidColor(IntUiDarkTheme.colors.grey(4)),
        borderFocused: Brush = SolidColor(IntUiDarkTheme.colors.grey(1)),
        borderPressed: Brush = border,
        borderHovered: Brush = border,
    ) = ButtonColors(
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
    )
}

val ButtonColors.Companion.Outlined: IntUiOutlinedButtonColorFactory
    get() = IntUiOutlinedButtonColorFactory

object IntUiOutlinedButtonColorFactory {

    @Composable
    fun light(
        background: Brush = SolidColor(Color.Transparent),
        backgroundDisabled: Brush = SolidColor(IntUiLightTheme.colors.grey(12)),
        backgroundFocused: Brush = SolidColor(Color.Transparent),
        backgroundPressed: Brush = SolidColor(IntUiLightTheme.colors.grey(13)),
        backgroundHovered: Brush = SolidColor(Color.Transparent),
        content: Color = IntUiLightTheme.colors.grey(1),
        contentDisabled: Color = IntUiLightTheme.colors.grey(8),
        contentFocused: Color = IntUiLightTheme.colors.grey(1),
        contentPressed: Color = IntUiLightTheme.colors.grey(1),
        contentHovered: Color = IntUiLightTheme.colors.grey(1),
        border: Brush = SolidColor(IntUiLightTheme.colors.grey(9)),
        borderDisabled: Brush = SolidColor(IntUiLightTheme.colors.blue(11)),
        borderFocused: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
        borderPressed: Brush = SolidColor(IntUiLightTheme.colors.grey(7)),
        borderHovered: Brush = SolidColor(IntUiLightTheme.colors.grey(8)),
    ) = ButtonColors(
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
    )

    @Composable
    fun dark(
        background: Brush = SolidColor(Color.Transparent),
        backgroundDisabled: Brush = SolidColor(IntUiDarkTheme.colors.grey(5)),
        backgroundFocused: Brush = SolidColor(IntUiDarkTheme.colors.grey(6)),
        backgroundPressed: Brush = SolidColor(IntUiDarkTheme.colors.grey(2)),
        backgroundHovered: Brush = SolidColor(Color.Unspecified),
        content: Color = IntUiDarkTheme.colors.grey(12),
        contentDisabled: Color = IntUiDarkTheme.colors.grey(8),
        contentFocused: Color = IntUiDarkTheme.colors.grey(12),
        contentPressed: Color = IntUiDarkTheme.colors.grey(12),
        contentHovered: Color = IntUiDarkTheme.colors.grey(12),
        border: Brush = SolidColor(IntUiDarkTheme.colors.grey(5)),
        borderDisabled: Brush = SolidColor(IntUiDarkTheme.colors.blue(4)),
        borderFocused: Brush = SolidColor(IntUiDarkTheme.colors.grey(2)),
        borderPressed: Brush = SolidColor(IntUiDarkTheme.colors.grey(7)),
        borderHovered: Brush = SolidColor(IntUiDarkTheme.colors.grey(7)),
    ) = ButtonColors(
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
    )
}

fun ButtonMetrics.Companion.default(
    cornerSize: CornerSize = CornerSize(4.dp),
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    minSize: DpSize = DpSize(72.dp, 28.dp),
    borderWidth: Dp = 0.dp,
) = ButtonMetrics(cornerSize, padding, minSize, borderWidth)

fun ButtonMetrics.Companion.outlined(
    cornerSize: CornerSize = CornerSize(4.dp),
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    minSize: DpSize = DpSize(72.dp, 28.dp),
    borderWidth: Dp = 1.dp,
) = ButtonMetrics(cornerSize, padding, minSize, borderWidth)
