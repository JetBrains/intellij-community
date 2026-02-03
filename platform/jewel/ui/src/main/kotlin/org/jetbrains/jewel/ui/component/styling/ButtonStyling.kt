package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ButtonState

@Stable
@GenerateDataFunctions
public class ButtonStyle(
    public val colors: ButtonColors,
    public val metrics: ButtonMetrics,
    public val focusOutlineAlignment: Stroke.Alignment,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ButtonStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (focusOutlineAlignment != other.focusOutlineAlignment) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + focusOutlineAlignment.hashCode()
        return result
    }

    override fun toString(): String {
        return "ButtonStyle(" +
            "colors=$colors, " +
            "metrics=$metrics, " +
            "focusOutlineAlignment=$focusOutlineAlignment" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class ButtonColors(
    public val background: Brush,
    public val backgroundDisabled: Brush,
    public val backgroundFocused: Brush,
    public val backgroundPressed: Brush,
    public val backgroundHovered: Brush,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val border: Brush,
    public val borderDisabled: Brush,
    public val borderFocused: Brush,
    public val borderPressed: Brush,
    public val borderHovered: Brush,
) {
    @Composable
    public fun backgroundFor(state: ButtonState): State<Brush> =
        rememberUpdatedState(
            state.chooseValue(
                normal = background,
                disabled = backgroundDisabled,
                focused = backgroundFocused,
                pressed = backgroundPressed,
                hovered = backgroundHovered,
                active = background,
            )
        )

    @Composable
    public fun contentFor(state: ButtonState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = content,
                disabled = contentDisabled,
                focused = contentFocused,
                pressed = contentPressed,
                hovered = contentHovered,
                active = content,
            )
        )

    @Composable
    public fun borderFor(state: ButtonState): State<Brush> =
        rememberUpdatedState(
            if (JewelTheme.isSwingCompatMode) {
                state.chooseValue(
                    normal = border,
                    disabled = borderDisabled,
                    focused = borderFocused,
                    pressed = borderPressed,
                    hovered = borderHovered,
                    active = border,
                )
            } else {
                when {
                    !state.isEnabled -> borderDisabled
                    state.isFocused -> borderFocused
                    state.isPressed -> borderPressed
                    state.isHovered -> borderHovered
                    else -> border
                }
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ButtonColors

        if (background != other.background) return false
        if (backgroundDisabled != other.backgroundDisabled) return false
        if (backgroundFocused != other.backgroundFocused) return false
        if (backgroundPressed != other.backgroundPressed) return false
        if (backgroundHovered != other.backgroundHovered) return false
        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentFocused != other.contentFocused) return false
        if (contentPressed != other.contentPressed) return false
        if (contentHovered != other.contentHovered) return false
        if (border != other.border) return false
        if (borderDisabled != other.borderDisabled) return false
        if (borderFocused != other.borderFocused) return false
        if (borderPressed != other.borderPressed) return false
        if (borderHovered != other.borderHovered) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundDisabled.hashCode()
        result = 31 * result + backgroundFocused.hashCode()
        result = 31 * result + backgroundPressed.hashCode()
        result = 31 * result + backgroundHovered.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentFocused.hashCode()
        result = 31 * result + contentPressed.hashCode()
        result = 31 * result + contentHovered.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + borderDisabled.hashCode()
        result = 31 * result + borderFocused.hashCode()
        result = 31 * result + borderPressed.hashCode()
        result = 31 * result + borderHovered.hashCode()
        return result
    }

    override fun toString(): String {
        return "ButtonColors(" +
            "background=$background, " +
            "backgroundDisabled=$backgroundDisabled, " +
            "backgroundFocused=$backgroundFocused, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "contentFocused=$contentFocused, " +
            "contentPressed=$contentPressed, " +
            "contentHovered=$contentHovered, " +
            "border=$border, " +
            "borderDisabled=$borderDisabled, " +
            "borderFocused=$borderFocused, " +
            "borderPressed=$borderPressed, " +
            "borderHovered=$borderHovered" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class ButtonMetrics(
    public val cornerSize: CornerSize,
    public val padding: PaddingValues,
    public val minSize: DpSize,
    public val borderWidth: Dp,
    public val focusOutlineExpand: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ButtonMetrics

        if (cornerSize != other.cornerSize) return false
        if (padding != other.padding) return false
        if (minSize != other.minSize) return false
        if (borderWidth != other.borderWidth) return false
        if (focusOutlineExpand != other.focusOutlineExpand) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + minSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + focusOutlineExpand.hashCode()
        return result
    }

    override fun toString(): String {
        return "ButtonMetrics(" +
            "cornerSize=$cornerSize, " +
            "padding=$padding, " +
            "minSize=$minSize, " +
            "borderWidth=$borderWidth, " +
            "focusOutlineExpand=$focusOutlineExpand" +
            ")"
    }

    public companion object
}

public val LocalDefaultButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No default ButtonStyle provided. Have you forgotten the theme?")
}

public val LocalOutlinedButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No outlined ButtonStyle provided. Have you forgotten the theme?")
}
