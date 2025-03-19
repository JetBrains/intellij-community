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
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ChipState

@Stable
@GenerateDataFunctions
public class ChipStyle(public val colors: ChipColors, public val metrics: ChipMetrics) {
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class ChipColors(
    public val background: Brush,
    public val backgroundDisabled: Brush,
    public val backgroundFocused: Brush,
    public val backgroundPressed: Brush,
    public val backgroundHovered: Brush,
    public val backgroundSelected: Brush,
    public val backgroundSelectedDisabled: Brush,
    public val backgroundSelectedPressed: Brush,
    public val backgroundSelectedFocused: Brush,
    public val backgroundSelectedHovered: Brush,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val contentSelected: Color,
    public val contentSelectedDisabled: Color,
    public val contentSelectedPressed: Color,
    public val contentSelectedFocused: Color,
    public val contentSelectedHovered: Color,
    public val border: Color,
    public val borderDisabled: Color,
    public val borderFocused: Color,
    public val borderPressed: Color,
    public val borderHovered: Color,
    public val borderSelected: Color,
    public val borderSelectedDisabled: Color,
    public val borderSelectedPressed: Color,
    public val borderSelectedFocused: Color,
    public val borderSelectedHovered: Color,
) {
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class ChipMetrics(
    public val cornerSize: CornerSize,
    public val padding: PaddingValues,
    public val borderWidth: Dp,
    public val borderWidthSelected: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChipMetrics

        if (cornerSize != other.cornerSize) return false
        if (padding != other.padding) return false
        if (borderWidth != other.borderWidth) return false
        if (borderWidthSelected != other.borderWidthSelected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + borderWidthSelected.hashCode()
        return result
    }

    override fun toString(): String {
        return "ChipMetrics(" +
            "cornerSize=$cornerSize, " +
            "padding=$padding, " +
            "borderWidth=$borderWidth, " +
            "borderWidthSelected=$borderWidthSelected" +
            ")"
    }

    public companion object
}

public val LocalChipStyle: ProvidableCompositionLocal<ChipStyle> = staticCompositionLocalOf {
    error("No ChipStyle provided. Have you forgotten the theme?")
}
