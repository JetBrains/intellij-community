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
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ButtonState

/** Combines [ButtonColors] and [ButtonMetrics] styling sub-objects for a button component. */
@Stable
@GenerateDataFunctions
public class ButtonStyle(
    /** The color tokens for the button in its various interaction states. */
    public val colors: ButtonColors,
    /** The size and spacing metrics for the button. */
    public val metrics: ButtonMetrics,
    /** The alignment of the focus outline stroke relative to the button border. */
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

    /** Companion object for [ButtonStyle]. */
    public companion object
}

/** Holds color tokens for the button component in its various interaction states. */
@Immutable
@GenerateDataFunctions
public class ButtonColors(
    /** The background brush in the normal state. */
    public val background: Brush,
    /** The background brush when the button is disabled. */
    public val backgroundDisabled: Brush,
    /** The background brush when the button is focused. */
    public val backgroundFocused: Brush,
    /** The background brush when the button is pressed. */
    public val backgroundPressed: Brush,
    /** The background brush when the button is hovered. */
    public val backgroundHovered: Brush,
    /** The content (foreground) color in the normal state. */
    public val content: Color,
    /** The content color when the button is disabled. */
    public val contentDisabled: Color,
    /** The content color when the button is focused. */
    public val contentFocused: Color,
    /** The content color when the button is pressed. */
    public val contentPressed: Color,
    /** The content color when the button is hovered. */
    public val contentHovered: Color,
    /** The border brush in the normal state. */
    public val border: Brush,
    /** The border brush when the button is disabled. */
    public val borderDisabled: Brush,
    /** The border brush when the button is focused. */
    public val borderFocused: Brush,
    /** The border brush when the button is pressed. */
    public val borderPressed: Brush,
    /** The border brush when the button is hovered. */
    public val borderHovered: Brush,
) {
    /** Returns a [State] holding the background brush appropriate for the given [state]. */
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

    /** Returns a [State] holding the content color appropriate for the given [state]. */
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

    /**
     * Returns a [State] holding the border brush appropriate for the given [state], taking Swing compatibility mode
     * into account.
     */
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

    /** Companion object for [ButtonColors]. */
    public companion object
}

/** Holds size and spacing metrics for the button component. */
@Stable
@GenerateDataFunctions
public class ButtonMetrics(
    /** The corner radius of the button. */
    public val cornerSize: CornerSize,
    /** The inner padding of the button content. */
    public val padding: PaddingValues,
    /** The minimum width and height of the button. */
    public val minSize: DpSize,
    /** The width of the button border stroke. */
    public val borderWidth: Dp,
    /** The amount by which the focus outline expands beyond the button border. */
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

    /** Companion object for [ButtonMetrics]. */
    public companion object
}

/** CompositionLocal providing the [ButtonStyle] for default (filled) buttons. */
public val LocalDefaultButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No default ButtonStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the [ButtonStyle] for outlined buttons. */
public val LocalOutlinedButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No outlined ButtonStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the [ButtonStyle] for default (filled) slim buttons. */
public val LocalDefaultSlimButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No default slim ButtonStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the [ButtonStyle] for outlined slim buttons. */
public val LocalOutlinedSlimButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No outlined slim ButtonStyle provided. Have you forgotten the theme?")
}

/** Creating a fallback style for compatibility with older versions. */
internal fun fallbackDefaultSlimButtonStyle(colors: ButtonColors): ButtonStyle {
    val metrics =
        ButtonMetrics(
            cornerSize = CornerSize(4.dp),
            padding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            minSize = DpSize(60.dp, 24.dp),
            borderWidth = 1.dp,
            focusOutlineExpand = 1.5.dp,
        )
    return ButtonStyle(colors, metrics, Stroke.Alignment.Center)
}

/** Creating a fallback style for compatibility with older versions. */
internal fun fallbackOutlinedSlimButtonStyle(colors: ButtonColors): ButtonStyle {
    val metrics =
        ButtonMetrics(
            cornerSize = CornerSize(4.dp),
            padding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            minSize = DpSize(60.dp, 24.dp),
            borderWidth = 1.dp,
            focusOutlineExpand = Dp.Unspecified,
        )
    return ButtonStyle(colors, metrics, Stroke.Alignment.Center)
}
