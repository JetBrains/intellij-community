@file:Suppress("DEPRECATION")

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.DropdownState
import org.jetbrains.jewel.ui.icon.IconKey

@Stable
@GenerateDataFunctions
public class DropdownStyle(
    public val colors: DropdownColors,
    public val metrics: DropdownMetrics,
    public val icons: DropdownIcons,
    public val menuStyle: MenuStyle,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DropdownStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false
        if (menuStyle != other.menuStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        result = 31 * result + menuStyle.hashCode()
        return result
    }

    override fun toString(): String {
        return "DropdownStyle(" +
            "colors=$colors, " +
            "metrics=$metrics, " +
            "icons=$icons, " +
            "menuStyle=$menuStyle" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class DropdownColors(
    public val background: Color,
    public val backgroundDisabled: Color,
    public val backgroundFocused: Color,
    public val backgroundPressed: Color,
    public val backgroundHovered: Color,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val border: Color,
    public val borderDisabled: Color,
    public val borderFocused: Color,
    public val borderPressed: Color,
    public val borderHovered: Color,
    public val iconTint: Color,
    public val iconTintDisabled: Color,
    public val iconTintFocused: Color,
    public val iconTintPressed: Color,
    public val iconTintHovered: Color,
) {
    @Composable
    public fun backgroundFor(state: DropdownState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                state.isFocused -> backgroundFocused
                state.isActive -> background
                else -> background
            }
        )

    @Composable
    public fun contentFor(state: DropdownState): State<Color> =
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
    public fun borderFor(state: DropdownState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = border,
                disabled = borderDisabled,
                focused = borderFocused,
                pressed = borderPressed,
                hovered = borderHovered,
                active = border,
            )
        )

    @Composable
    public fun iconTintFor(state: DropdownState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = iconTint,
                disabled = iconTintDisabled,
                focused = iconTintFocused,
                pressed = iconTintPressed,
                hovered = iconTintHovered,
                active = iconTint,
            )
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DropdownColors

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
        if (iconTint != other.iconTint) return false
        if (iconTintDisabled != other.iconTintDisabled) return false
        if (iconTintFocused != other.iconTintFocused) return false
        if (iconTintPressed != other.iconTintPressed) return false
        if (iconTintHovered != other.iconTintHovered) return false

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
        result = 31 * result + iconTint.hashCode()
        result = 31 * result + iconTintDisabled.hashCode()
        result = 31 * result + iconTintFocused.hashCode()
        result = 31 * result + iconTintPressed.hashCode()
        result = 31 * result + iconTintHovered.hashCode()
        return result
    }

    override fun toString(): String {
        return "DropdownColors(" +
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
            "borderHovered=$borderHovered, " +
            "iconTint=$iconTint, " +
            "iconTintDisabled=$iconTintDisabled, " +
            "iconTintFocused=$iconTintFocused, " +
            "iconTintPressed=$iconTintPressed, " +
            "iconTintHovered=$iconTintHovered" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class DropdownMetrics(
    public val arrowMinSize: DpSize,
    public val minSize: DpSize,
    public val cornerSize: CornerSize,
    public val contentPadding: PaddingValues,
    public val borderWidth: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DropdownMetrics

        if (arrowMinSize != other.arrowMinSize) return false
        if (minSize != other.minSize) return false
        if (cornerSize != other.cornerSize) return false
        if (contentPadding != other.contentPadding) return false
        if (borderWidth != other.borderWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = arrowMinSize.hashCode()
        result = 31 * result + minSize.hashCode()
        result = 31 * result + cornerSize.hashCode()
        result = 31 * result + contentPadding.hashCode()
        result = 31 * result + borderWidth.hashCode()
        return result
    }

    override fun toString(): String {
        return "DropdownMetrics(" +
            "arrowMinSize=$arrowMinSize, " +
            "minSize=$minSize, " +
            "cornerSize=$cornerSize, " +
            "contentPadding=$contentPadding, " +
            "borderWidth=$borderWidth" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class DropdownIcons(public val chevronDown: IconKey) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DropdownIcons

        return chevronDown == other.chevronDown
    }

    override fun hashCode(): Int = chevronDown.hashCode()

    override fun toString(): String = "DropdownIcons(chevronDown=$chevronDown)"

    public companion object
}

public val LocalDefaultDropdownStyle: ProvidableCompositionLocal<DropdownStyle> = staticCompositionLocalOf {
    error("No DefaultDropdownStyle provided. Have you forgotten the theme?")
}

public val LocalUndecoratedDropdownStyle: ProvidableCompositionLocal<DropdownStyle> = staticCompositionLocalOf {
    error("No UndecoratedDropdownStyle provided. Have you forgotten the theme?")
}
