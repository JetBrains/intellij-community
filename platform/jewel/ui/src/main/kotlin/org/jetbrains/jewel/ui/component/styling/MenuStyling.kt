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
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.MenuItemState
import org.jetbrains.jewel.ui.icon.IconKey

/** Combines colors, metrics, and icons that define the appearance of a menu component. */
@Stable
@GenerateDataFunctions
public class MenuStyle(
    /** Whether the menu is rendered in dark mode. */
    public val isDark: Boolean,
    /** The color tokens for the menu. */
    public val colors: MenuColors,
    /** The size and spacing metrics for the menu. */
    public val metrics: MenuMetrics,
    /** The icon keys for the menu. */
    public val icons: MenuIcons,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuStyle

        if (isDark != other.isDark) return false
        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        return result
    }

    override fun toString(): String = "MenuStyle(isDark=$isDark, colors=$colors, metrics=$metrics, icons=$icons)"

    /** Companion object for [MenuStyle]. */
    public companion object
}

/** Holds color tokens for the menu component, including background, border, shadow, and per-item colors. */
@Immutable
@GenerateDataFunctions
public class MenuColors(
    /** The background color of the menu. */
    public val background: Color,
    /** The border color of the menu. */
    public val border: Color,
    /** The shadow color of the menu. */
    public val shadow: Color,
    /** The color tokens for menu items. */
    public val itemColors: MenuItemColors,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuColors

        if (background != other.background) return false
        if (border != other.border) return false
        if (shadow != other.shadow) return false
        if (itemColors != other.itemColors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + shadow.hashCode()
        result = 31 * result + itemColors.hashCode()
        return result
    }

    override fun toString(): String {
        return "MenuColors(" +
            "background=$background, " +
            "border=$border, " +
            "shadow=$shadow, " +
            "itemColors=$itemColors" +
            ")"
    }

    /** Companion object for [MenuColors]. */
    public companion object
}

/** Holds color tokens for a menu item in its various states (normal, disabled, focused, pressed, hovered). */
@Immutable
@GenerateDataFunctions
public class MenuItemColors(
    /** The background color of a menu item in its normal state. */
    public val background: Color,
    /** The background color of a menu item in its disabled state. */
    public val backgroundDisabled: Color,
    /** The background color of a menu item in its focused state. */
    public val backgroundFocused: Color,
    /** The background color of a menu item in its pressed state. */
    public val backgroundPressed: Color,
    /** The background color of a menu item in its hovered state. */
    public val backgroundHovered: Color,
    /** The content (text) color of a menu item in its normal state. */
    public val content: Color,
    /** The content (text) color of a menu item in its disabled state. */
    public val contentDisabled: Color,
    /** The content (text) color of a menu item in its focused state. */
    public val contentFocused: Color,
    /** The content (text) color of a menu item in its pressed state. */
    public val contentPressed: Color,
    /** The content (text) color of a menu item in its hovered state. */
    public val contentHovered: Color,
    /** The icon tint color of a menu item in its normal state. */
    public val iconTint: Color,
    /** The icon tint color of a menu item in its disabled state. */
    public val iconTintDisabled: Color,
    /** The icon tint color of a menu item in its focused state. */
    public val iconTintFocused: Color,
    /** The icon tint color of a menu item in its pressed state. */
    public val iconTintPressed: Color,
    /** The icon tint color of a menu item in its hovered state. */
    public val iconTintHovered: Color,
    /** The keybinding hint tint color of a menu item in its normal state. */
    public val keybindingTint: Color,
    /** The keybinding hint tint color of a menu item in its disabled state. */
    public val keybindingTintDisabled: Color,
    /** The keybinding hint tint color of a menu item in its focused state. */
    public val keybindingTintFocused: Color,
    /** The keybinding hint tint color of a menu item in its pressed state. */
    public val keybindingTintPressed: Color,
    /** The keybinding hint tint color of a menu item in its hovered state. */
    public val keybindingTintHovered: Color,
    /** The color of the separator line between menu items. */
    public val separator: Color,
) {
    /** Returns a [State] holding the background color appropriate for the given [state]. */
    @Composable
    internal fun backgroundFor(state: MenuItemState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = background,
                disabled = backgroundDisabled,
                active = background,
                focused = backgroundFocused,
                pressed = backgroundPressed,
                hovered = backgroundHovered,
            )
        )

    /** Returns a [State] holding the content (text) color appropriate for the given [state]. */
    @Composable
    internal fun contentFor(state: MenuItemState): State<Color> =
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

    /** Returns a [State] holding the icon tint color appropriate for the given [state]. */
    @Composable
    internal fun iconTintFor(state: MenuItemState): State<Color> =
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

    /** Returns a [State] holding the keybinding tint color appropriate for the given [state]. */
    @Composable
    internal fun keybindingTintFor(state: MenuItemState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = keybindingTint,
                disabled = keybindingTintDisabled,
                focused = keybindingTintFocused,
                pressed = keybindingTintPressed,
                hovered = keybindingTintHovered,
                active = keybindingTint,
            )
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuItemColors

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
        if (iconTint != other.iconTint) return false
        if (iconTintDisabled != other.iconTintDisabled) return false
        if (iconTintFocused != other.iconTintFocused) return false
        if (iconTintPressed != other.iconTintPressed) return false
        if (iconTintHovered != other.iconTintHovered) return false
        if (keybindingTint != other.keybindingTint) return false
        if (keybindingTintDisabled != other.keybindingTintDisabled) return false
        if (keybindingTintFocused != other.keybindingTintFocused) return false
        if (keybindingTintPressed != other.keybindingTintPressed) return false
        if (keybindingTintHovered != other.keybindingTintHovered) return false
        if (separator != other.separator) return false

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
        result = 31 * result + iconTint.hashCode()
        result = 31 * result + iconTintDisabled.hashCode()
        result = 31 * result + iconTintFocused.hashCode()
        result = 31 * result + iconTintPressed.hashCode()
        result = 31 * result + iconTintHovered.hashCode()
        result = 31 * result + keybindingTint.hashCode()
        result = 31 * result + keybindingTintDisabled.hashCode()
        result = 31 * result + keybindingTintFocused.hashCode()
        result = 31 * result + keybindingTintPressed.hashCode()
        result = 31 * result + keybindingTintHovered.hashCode()
        result = 31 * result + separator.hashCode()
        return result
    }

    override fun toString(): String {
        return "MenuItemColors(" +
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
            "iconTint=$iconTint, " +
            "iconTintDisabled=$iconTintDisabled, " +
            "iconTintFocused=$iconTintFocused, " +
            "iconTintPressed=$iconTintPressed, " +
            "iconTintHovered=$iconTintHovered, " +
            "keybindingTint=$keybindingTint, " +
            "keybindingTintDisabled=$keybindingTintDisabled, " +
            "keybindingTintFocused=$keybindingTintFocused, " +
            "keybindingTintPressed=$keybindingTintPressed, " +
            "keybindingTintHovered=$keybindingTintHovered, " +
            "separator=$separator" +
            ")"
    }

    /** Companion object for [MenuItemColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the menu component, including corner size, padding, offset, shadow, and per-item
 * metrics.
 */
@Stable
@GenerateDataFunctions
public class MenuMetrics(
    /** The corner radius of the menu popup. */
    public val cornerSize: CornerSize,
    /** The outer margin around the menu popup. */
    public val menuMargin: PaddingValues,
    /** The inner content padding of the menu popup. */
    public val contentPadding: PaddingValues,
    /** The display offset of the menu popup relative to its anchor. */
    public val offset: DpOffset,
    /** The size of the drop shadow behind the menu popup. */
    public val shadowSize: Dp,
    /** The width of the menu popup border. */
    public val borderWidth: Dp,
    /** The size and spacing metrics for menu items. */
    public val itemMetrics: MenuItemMetrics,
    /** The size and spacing metrics for submenus. */
    public val submenuMetrics: SubmenuMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuMetrics

        if (cornerSize != other.cornerSize) return false
        if (menuMargin != other.menuMargin) return false
        if (contentPadding != other.contentPadding) return false
        if (offset != other.offset) return false
        if (shadowSize != other.shadowSize) return false
        if (borderWidth != other.borderWidth) return false
        if (itemMetrics != other.itemMetrics) return false
        if (submenuMetrics != other.submenuMetrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + menuMargin.hashCode()
        result = 31 * result + contentPadding.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + shadowSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + itemMetrics.hashCode()
        result = 31 * result + submenuMetrics.hashCode()
        return result
    }

    override fun toString(): String {
        return "MenuMetrics(" +
            "cornerSize=$cornerSize, " +
            "menuMargin=$menuMargin, " +
            "contentPadding=$contentPadding, " +
            "offset=$offset, " +
            "shadowSize=$shadowSize, " +
            "borderWidth=$borderWidth, " +
            "itemMetrics=$itemMetrics, " +
            "submenuMetrics=$submenuMetrics" +
            ")"
    }

    /** Companion object for [MenuMetrics]. */
    public companion object
}

/**
 * Holds size and spacing metrics for a menu item, including padding, separator dimensions, icon size, and minimum
 * height.
 */
@Stable
@GenerateDataFunctions
public class MenuItemMetrics(
    /** The corner radius of the selection highlight for a menu item. */
    public val selectionCornerSize: CornerSize,
    /** The outer padding around the menu item row. */
    public val outerPadding: PaddingValues,
    /** The inner content padding within the menu item row. */
    public val contentPadding: PaddingValues,
    /** The padding around the separator line. */
    public val separatorPadding: PaddingValues,
    /** The padding around the keybinding hint text. */
    public val keybindingsPadding: PaddingValues,
    /** The thickness of the separator line. */
    public val separatorThickness: Dp,
    /** The total height of the separator row. */
    public val separatorHeight: Dp,
    /** The size of the leading icon in a menu item. */
    public val iconSize: Dp,
    /** The minimum height of a menu item row. */
    public val minHeight: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuItemMetrics

        if (selectionCornerSize != other.selectionCornerSize) return false
        if (outerPadding != other.outerPadding) return false
        if (contentPadding != other.contentPadding) return false
        if (separatorPadding != other.separatorPadding) return false
        if (keybindingsPadding != other.keybindingsPadding) return false
        if (separatorThickness != other.separatorThickness) return false
        if (separatorHeight != other.separatorHeight) return false
        if (iconSize != other.iconSize) return false
        if (minHeight != other.minHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = selectionCornerSize.hashCode()
        result = 31 * result + outerPadding.hashCode()
        result = 31 * result + contentPadding.hashCode()
        result = 31 * result + separatorPadding.hashCode()
        result = 31 * result + keybindingsPadding.hashCode()
        result = 31 * result + separatorThickness.hashCode()
        result = 31 * result + separatorHeight.hashCode()
        result = 31 * result + iconSize.hashCode()
        result = 31 * result + minHeight.hashCode()
        return result
    }

    override fun toString(): String {
        return "MenuItemMetrics(" +
            "selectionCornerSize=$selectionCornerSize, " +
            "outerPadding=$outerPadding, " +
            "contentPadding=$contentPadding, " +
            "separatorPadding=$separatorPadding, " +
            "keybindingsPadding=$keybindingsPadding, " +
            "separatorThickness=$separatorThickness, " +
            "separatorHeight=$separatorHeight, " +
            "iconSize=$iconSize, " +
            "minHeight=$minHeight" +
            ")"
    }

    /** Companion object for [MenuItemMetrics]. */
    public companion object
}

/** Holds size and spacing metrics for a submenu, specifically the display offset relative to its parent item. */
@Stable
@GenerateDataFunctions
public class SubmenuMetrics(
    /** The display offset of a submenu popup relative to its parent item. */
    public val offset: DpOffset
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SubmenuMetrics

        return offset == other.offset
    }

    override fun hashCode(): Int = offset.hashCode()

    override fun toString(): String = "SubmenuMetrics(offset=$offset)"

    /** Companion object for [SubmenuMetrics]. */
    public companion object
}

/** Holds icon keys for the menu component, including the submenu chevron indicator. */
@Immutable
@GenerateDataFunctions
public class MenuIcons(
    /** The icon key for the submenu chevron indicator. */
    public val submenuChevron: IconKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuIcons

        return submenuChevron == other.submenuChevron
    }

    override fun hashCode(): Int = submenuChevron.hashCode()

    override fun toString(): String = "MenuIcons(submenuChevron=$submenuChevron)"

    /** Companion object for [MenuIcons]. */
    public companion object
}

/** CompositionLocal used to provide the [MenuStyle] to menu components in the hierarchy. */
public val LocalMenuStyle: ProvidableCompositionLocal<MenuStyle> = staticCompositionLocalOf {
    error("No MenuStyle provided. Have you forgotten the theme?")
}
