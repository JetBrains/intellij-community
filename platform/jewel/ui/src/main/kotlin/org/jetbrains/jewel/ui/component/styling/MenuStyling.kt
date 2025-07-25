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

@Stable
@GenerateDataFunctions
public class MenuStyle(
    public val isDark: Boolean,
    public val colors: MenuColors,
    public val metrics: MenuMetrics,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class MenuColors(
    public val background: Color,
    public val border: Color,
    public val shadow: Color,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class MenuItemColors(
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
    public val iconTint: Color,
    public val iconTintDisabled: Color,
    public val iconTintFocused: Color,
    public val iconTintPressed: Color,
    public val iconTintHovered: Color,
    public val keybindingTint: Color,
    public val keybindingTintDisabled: Color,
    public val keybindingTintFocused: Color,
    public val keybindingTintPressed: Color,
    public val keybindingTintHovered: Color,
    public val separator: Color,
) {
    @Composable
    public fun backgroundFor(state: MenuItemState): State<Color> =
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

    @Composable
    public fun contentFor(state: MenuItemState): State<Color> =
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
    public fun iconTintFor(state: MenuItemState): State<Color> =
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

    @Composable
    public fun keybindingTintFor(state: MenuItemState): State<Color> =
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class MenuMetrics(
    public val cornerSize: CornerSize,
    public val menuMargin: PaddingValues,
    public val contentPadding: PaddingValues,
    public val offset: DpOffset,
    public val shadowSize: Dp,
    public val borderWidth: Dp,
    public val itemMetrics: MenuItemMetrics,
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class MenuItemMetrics(
    public val selectionCornerSize: CornerSize,
    public val outerPadding: PaddingValues,
    public val contentPadding: PaddingValues,
    public val separatorPadding: PaddingValues,
    public val keybindingsPadding: PaddingValues,
    public val separatorThickness: Dp,
    public val separatorHeight: Dp,
    public val iconSize: Dp,
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class SubmenuMetrics(public val offset: DpOffset) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SubmenuMetrics

        return offset == other.offset
    }

    override fun hashCode(): Int = offset.hashCode()

    override fun toString(): String = "SubmenuMetrics(offset=$offset)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class MenuIcons(public val submenuChevron: IconKey) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuIcons

        return submenuChevron == other.submenuChevron
    }

    override fun hashCode(): Int = submenuChevron.hashCode()

    override fun toString(): String = "MenuIcons(submenuChevron=$submenuChevron)"

    public companion object
}

public val LocalMenuStyle: ProvidableCompositionLocal<MenuStyle> = staticCompositionLocalOf {
    error("No MenuStyle provided. Have you forgotten the theme?")
}
