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

/** Combines the colors, metrics, icons, and menu style that define the appearance of a Dropdown component. */
@Stable
@GenerateDataFunctions
public class DropdownStyle(
    /** The color tokens for the dropdown in its various states. */
    public val colors: DropdownColors,
    /** The size and spacing metrics for the dropdown. */
    public val metrics: DropdownMetrics,
    /** The icon keys used by the dropdown. */
    public val icons: DropdownIcons,
    /** The style applied to the dropdown's popup menu. */
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

    /** Companion object for [DropdownStyle]. */
    public companion object
}

/** Holds color tokens for the Dropdown component in its various interaction states. */
@Immutable
@GenerateDataFunctions
public class DropdownColors(
    /** The background color in the normal state. */
    public val background: Color,
    /** The background color when the dropdown is disabled. */
    public val backgroundDisabled: Color,
    /** The background color when the dropdown is focused. */
    public val backgroundFocused: Color,
    /** The background color when the dropdown is pressed. */
    public val backgroundPressed: Color,
    /** The background color when the dropdown is hovered. */
    public val backgroundHovered: Color,
    /** The content (text) color in the normal state. */
    public val content: Color,
    /** The content color when the dropdown is disabled. */
    public val contentDisabled: Color,
    /** The content color when the dropdown is focused. */
    public val contentFocused: Color,
    /** The content color when the dropdown is pressed. */
    public val contentPressed: Color,
    /** The content color when the dropdown is hovered. */
    public val contentHovered: Color,
    /** The border color in the normal state. */
    public val border: Color,
    /** The border color when the dropdown is disabled. */
    public val borderDisabled: Color,
    /** The border color when the dropdown is focused. */
    public val borderFocused: Color,
    /** The border color when the dropdown is pressed. */
    public val borderPressed: Color,
    /** The border color when the dropdown is hovered. */
    public val borderHovered: Color,
    /** The chevron icon tint color in the normal state. */
    public val iconTint: Color,
    /** The chevron icon tint color when the dropdown is disabled. */
    public val iconTintDisabled: Color,
    /** The chevron icon tint color when the dropdown is focused. */
    public val iconTintFocused: Color,
    /** The chevron icon tint color when the dropdown is pressed. */
    public val iconTintPressed: Color,
    /** The chevron icon tint color when the dropdown is hovered. */
    public val iconTintHovered: Color,
) {
    /** Returns a [State] holding the background color appropriate for the given [state]. */
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

    /** Returns a [State] holding the content color appropriate for the given [state]. */
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

    /** Returns a [State] holding the border color appropriate for the given [state]. */
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

    /** Returns a [State] holding the icon tint color appropriate for the given [state]. */
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

    /** Companion object for [DropdownColors]. */
    public companion object
}

/** Holds size and spacing metrics for the Dropdown component. */
@Stable
@GenerateDataFunctions
public class DropdownMetrics(
    /** The minimum size of the chevron arrow area. */
    public val arrowMinSize: DpSize,
    /** The minimum size of the dropdown control. */
    public val minSize: DpSize,
    /** The corner radius of the dropdown border. */
    public val cornerSize: CornerSize,
    /** The padding applied to the dropdown's content area. */
    public val contentPadding: PaddingValues,
    /** The width of the dropdown border. */
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

    /** Companion object for [DropdownMetrics]. */
    public companion object
}

/** Holds icon keys for the Dropdown component. */
@Immutable
@GenerateDataFunctions
public class DropdownIcons(
    /** The icon key for the chevron-down arrow shown in the dropdown. */
    public val chevronDown: IconKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DropdownIcons

        return chevronDown == other.chevronDown
    }

    override fun hashCode(): Int = chevronDown.hashCode()

    override fun toString(): String = "DropdownIcons(chevronDown=$chevronDown)"

    /** Companion object for [DropdownIcons]. */
    public companion object
}

/** CompositionLocal providing the default [DropdownStyle] for themed dropdown components. */
public val LocalDefaultDropdownStyle: ProvidableCompositionLocal<DropdownStyle> = staticCompositionLocalOf {
    error("No DefaultDropdownStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the undecorated [DropdownStyle] for borderless dropdown components. */
public val LocalUndecoratedDropdownStyle: ProvidableCompositionLocal<DropdownStyle> = staticCompositionLocalOf {
    error("No UndecoratedDropdownStyle provided. Have you forgotten the theme?")
}
