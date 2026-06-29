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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ChipState

/** Defines the styling for a Chip component, combining [ChipColors] and [ChipMetrics]. */
@Stable
@GenerateDataFunctions
public class ChipStyle(
    /** The color tokens used to paint the chip in its various states. */
    public val colors: ChipColors,
    /** The size and spacing metrics used to lay out the chip. */
    public val metrics: ChipMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChipStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "ChipStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [ChipStyle]. */
    public companion object
}

/**
 * Holds color tokens for the Chip component in its various states, including selected, focused, pressed, hovered, and
 * disabled.
 */
@Immutable
@GenerateDataFunctions
public class ChipColors(
    /** The background brush in the default state. */
    public val background: Brush,
    /** The background brush when the chip is disabled. */
    public val backgroundDisabled: Brush,
    /** The background brush when the chip is focused. */
    public val backgroundFocused: Brush,
    /** The background brush when the chip is pressed. */
    public val backgroundPressed: Brush,
    /** The background brush when the chip is hovered. */
    public val backgroundHovered: Brush,
    /** The background brush when the chip is selected. */
    public val backgroundSelected: Brush,
    /** The background brush when the chip is selected and disabled. */
    public val backgroundSelectedDisabled: Brush,
    /** The background brush when the chip is selected and pressed. */
    public val backgroundSelectedPressed: Brush,
    /** The background brush when the chip is selected and focused. */
    public val backgroundSelectedFocused: Brush,
    /** The background brush when the chip is selected and hovered. */
    public val backgroundSelectedHovered: Brush,
    /** The content color in the default state. */
    public val content: Color,
    /** The content color when the chip is disabled. */
    public val contentDisabled: Color,
    /** The content color when the chip is focused. */
    public val contentFocused: Color,
    /** The content color when the chip is pressed. */
    public val contentPressed: Color,
    /** The content color when the chip is hovered. */
    public val contentHovered: Color,
    /** The content color when the chip is selected. */
    public val contentSelected: Color,
    /** The content color when the chip is selected and disabled. */
    public val contentSelectedDisabled: Color,
    /** The content color when the chip is selected and pressed. */
    public val contentSelectedPressed: Color,
    /** The content color when the chip is selected and focused. */
    public val contentSelectedFocused: Color,
    /** The content color when the chip is selected and hovered. */
    public val contentSelectedHovered: Color,
    /** The border color in the default state. */
    public val border: Color,
    /** The border color when the chip is disabled. */
    public val borderDisabled: Color,
    /** The border color when the chip is focused. */
    public val borderFocused: Color,
    /** The border color when the chip is pressed. */
    public val borderPressed: Color,
    /** The border color when the chip is hovered. */
    public val borderHovered: Color,
    /** The border color when the chip is selected. */
    public val borderSelected: Color,
    /** The border color when the chip is selected and disabled. */
    public val borderSelectedDisabled: Color,
    /** The border color when the chip is selected and pressed. */
    public val borderSelectedPressed: Color,
    /** The border color when the chip is selected and focused. */
    public val borderSelectedFocused: Color,
    /** The border color when the chip is selected and hovered. */
    public val borderSelectedHovered: Color,
) {
    /**
     * Returns a [State] holding the background [Brush] appropriate for the given [state], accounting for selection,
     * enabled, pressed, focused, and hovered conditions.
     */
    @Composable
    public fun backgroundFor(state: ChipState): State<Brush> =
        rememberUpdatedState(
            if (state.isSelected) {
                when {
                    !state.isEnabled -> backgroundSelectedDisabled
                    state.isPressed -> backgroundSelectedPressed
                    state.isFocused -> backgroundSelectedFocused
                    state.isHovered -> backgroundSelectedHovered
                    else -> backgroundSelected
                }
            } else {
                when {
                    !state.isEnabled -> backgroundDisabled
                    state.isPressed -> backgroundPressed
                    state.isFocused -> backgroundFocused
                    state.isHovered -> backgroundHovered
                    else -> background
                }
            }
        )

    /**
     * Returns a [State] holding the content [Color] appropriate for the given [state], accounting for selection,
     * enabled, pressed, focused, and hovered conditions.
     */
    @Composable
    public fun contentFor(state: ChipState): State<Color> =
        rememberUpdatedState(
            if (state.isSelected) {
                when {
                    !state.isEnabled -> contentSelectedDisabled
                    state.isPressed -> contentSelectedPressed
                    state.isFocused -> contentSelectedFocused
                    state.isHovered -> contentSelectedHovered
                    else -> contentSelected
                }
            } else {
                when {
                    !state.isEnabled -> contentDisabled
                    state.isPressed -> contentPressed
                    state.isFocused -> contentFocused
                    state.isHovered -> contentHovered
                    else -> content
                }
            }
        )

    /**
     * Returns a [State] holding the border [Color] appropriate for the given [state], accounting for selection,
     * enabled, pressed, focused, and hovered conditions.
     */
    @Composable
    public fun borderFor(state: ChipState): State<Color> =
        rememberUpdatedState(
            if (state.isSelected) {
                when {
                    !state.isEnabled -> borderSelectedDisabled
                    state.isPressed && !JewelTheme.isSwingCompatMode -> borderSelectedPressed
                    state.isFocused -> borderSelectedFocused
                    state.isHovered && !JewelTheme.isSwingCompatMode -> borderSelectedHovered
                    else -> borderSelected
                }
            } else {
                when {
                    !state.isEnabled -> borderDisabled
                    state.isPressed && !JewelTheme.isSwingCompatMode -> borderPressed
                    state.isFocused -> borderFocused
                    state.isHovered && !JewelTheme.isSwingCompatMode -> borderHovered
                    else -> border
                }
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChipColors

        if (background != other.background) return false
        if (backgroundDisabled != other.backgroundDisabled) return false
        if (backgroundFocused != other.backgroundFocused) return false
        if (backgroundPressed != other.backgroundPressed) return false
        if (backgroundHovered != other.backgroundHovered) return false
        if (backgroundSelected != other.backgroundSelected) return false
        if (backgroundSelectedDisabled != other.backgroundSelectedDisabled) return false
        if (backgroundSelectedPressed != other.backgroundSelectedPressed) return false
        if (backgroundSelectedFocused != other.backgroundSelectedFocused) return false
        if (backgroundSelectedHovered != other.backgroundSelectedHovered) return false
        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentFocused != other.contentFocused) return false
        if (contentPressed != other.contentPressed) return false
        if (contentHovered != other.contentHovered) return false
        if (contentSelected != other.contentSelected) return false
        if (contentSelectedDisabled != other.contentSelectedDisabled) return false
        if (contentSelectedPressed != other.contentSelectedPressed) return false
        if (contentSelectedFocused != other.contentSelectedFocused) return false
        if (contentSelectedHovered != other.contentSelectedHovered) return false
        if (border != other.border) return false
        if (borderDisabled != other.borderDisabled) return false
        if (borderFocused != other.borderFocused) return false
        if (borderPressed != other.borderPressed) return false
        if (borderHovered != other.borderHovered) return false
        if (borderSelected != other.borderSelected) return false
        if (borderSelectedDisabled != other.borderSelectedDisabled) return false
        if (borderSelectedPressed != other.borderSelectedPressed) return false
        if (borderSelectedFocused != other.borderSelectedFocused) return false
        if (borderSelectedHovered != other.borderSelectedHovered) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundDisabled.hashCode()
        result = 31 * result + backgroundFocused.hashCode()
        result = 31 * result + backgroundPressed.hashCode()
        result = 31 * result + backgroundHovered.hashCode()
        result = 31 * result + backgroundSelected.hashCode()
        result = 31 * result + backgroundSelectedDisabled.hashCode()
        result = 31 * result + backgroundSelectedPressed.hashCode()
        result = 31 * result + backgroundSelectedFocused.hashCode()
        result = 31 * result + backgroundSelectedHovered.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentFocused.hashCode()
        result = 31 * result + contentPressed.hashCode()
        result = 31 * result + contentHovered.hashCode()
        result = 31 * result + contentSelected.hashCode()
        result = 31 * result + contentSelectedDisabled.hashCode()
        result = 31 * result + contentSelectedPressed.hashCode()
        result = 31 * result + contentSelectedFocused.hashCode()
        result = 31 * result + contentSelectedHovered.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + borderDisabled.hashCode()
        result = 31 * result + borderFocused.hashCode()
        result = 31 * result + borderPressed.hashCode()
        result = 31 * result + borderHovered.hashCode()
        result = 31 * result + borderSelected.hashCode()
        result = 31 * result + borderSelectedDisabled.hashCode()
        result = 31 * result + borderSelectedPressed.hashCode()
        result = 31 * result + borderSelectedFocused.hashCode()
        result = 31 * result + borderSelectedHovered.hashCode()
        return result
    }

    override fun toString(): String {
        return "ChipColors(" +
            "background=$background, " +
            "backgroundDisabled=$backgroundDisabled, " +
            "backgroundFocused=$backgroundFocused, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "backgroundSelected=$backgroundSelected, " +
            "backgroundSelectedDisabled=$backgroundSelectedDisabled, " +
            "backgroundSelectedPressed=$backgroundSelectedPressed, " +
            "backgroundSelectedFocused=$backgroundSelectedFocused, " +
            "backgroundSelectedHovered=$backgroundSelectedHovered, " +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "contentFocused=$contentFocused, " +
            "contentPressed=$contentPressed, " +
            "contentHovered=$contentHovered, " +
            "contentSelected=$contentSelected, " +
            "contentSelectedDisabled=$contentSelectedDisabled, " +
            "contentSelectedPressed=$contentSelectedPressed, " +
            "contentSelectedFocused=$contentSelectedFocused, " +
            "contentSelectedHovered=$contentSelectedHovered, " +
            "border=$border, " +
            "borderDisabled=$borderDisabled, " +
            "borderFocused=$borderFocused, " +
            "borderPressed=$borderPressed, " +
            "borderHovered=$borderHovered, " +
            "borderSelected=$borderSelected, " +
            "borderSelectedDisabled=$borderSelectedDisabled, " +
            "borderSelectedPressed=$borderSelectedPressed, " +
            "borderSelectedFocused=$borderSelectedFocused, " +
            "borderSelectedHovered=$borderSelectedHovered" +
            ")"
    }

    /** Companion object for [ChipColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the Chip component, including corner size, padding, border widths, and minimum
 * size.
 */
@Stable
@GenerateDataFunctions
public class ChipMetrics(
    /** The corner radius of the chip. */
    public val cornerSize: CornerSize,
    /** The inner padding applied to the chip content. */
    public val padding: PaddingValues,
    /** The width of the chip border in the default state. */
    public val borderWidth: Dp,
    /** The width of the chip border when selected. */
    public val borderWidthSelected: Dp,
    /** The minimum size of the chip. */
    public val minSize: DpSize,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChipMetrics

        if (cornerSize != other.cornerSize) return false
        if (padding != other.padding) return false
        if (borderWidth != other.borderWidth) return false
        if (borderWidthSelected != other.borderWidthSelected) return false
        if (minSize != other.minSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + borderWidthSelected.hashCode()
        result = 31 * result + minSize.hashCode()
        return result
    }

    override fun toString(): String {
        return "ChipMetrics(" +
            "cornerSize=$cornerSize, " +
            "padding=$padding, " +
            "borderWidth=$borderWidth, " +
            "borderWidthSelected=$borderWidthSelected," +
            "minSize=$minSize" +
            ")"
    }

    /** Companion object for [ChipMetrics]. */
    public companion object
}

/** CompositionLocal providing the current [ChipStyle]. */
public val LocalChipStyle: ProvidableCompositionLocal<ChipStyle> = staticCompositionLocalOf {
    error("No ChipStyle provided. Have you forgotten the theme?")
}
