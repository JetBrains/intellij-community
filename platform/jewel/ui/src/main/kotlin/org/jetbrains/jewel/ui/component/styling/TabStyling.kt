package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.icon.IconKey

@Stable
@GenerateDataFunctions
public class TabStyle(
    public val colors: TabColors,
    public val metrics: TabMetrics,
    public val icons: TabIcons,
    public val contentAlpha: TabContentAlpha,
    public val scrollbarStyle: ScrollbarStyle,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false
        if (contentAlpha != other.contentAlpha) return false
        if (scrollbarStyle != other.scrollbarStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        result = 31 * result + contentAlpha.hashCode()
        result = 31 * result + scrollbarStyle.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabStyle(" +
            "colors=$colors, " +
            "metrics=$metrics, " +
            "icons=$icons, " +
            "contentAlpha=$contentAlpha, " +
            "scrollbarStyle=$scrollbarStyle" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class TabMetrics(
    public val underlineThickness: Dp,
    public val tabPadding: PaddingValues,
    public val tabHeight: Dp,
    public val tabContentSpacing: Dp,
    public val closeContentGap: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabMetrics

        if (underlineThickness != other.underlineThickness) return false
        if (tabPadding != other.tabPadding) return false
        if (tabHeight != other.tabHeight) return false
        if (tabContentSpacing != other.tabContentSpacing) return false
        if (closeContentGap != other.closeContentGap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = underlineThickness.hashCode()
        result = 31 * result + tabPadding.hashCode()
        result = 31 * result + tabHeight.hashCode()
        result = 31 * result + tabContentSpacing.hashCode()
        result = 31 * result + closeContentGap.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabMetrics(" +
            "underlineThickness=$underlineThickness, " +
            "tabPadding=$tabPadding, " +
            "tabHeight=$tabHeight, " +
            "tabContentSpacing=$tabContentSpacing, " +
            "closeContentGap=$closeContentGap" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TabIcons(public val close: IconKey) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabIcons

        return close == other.close
    }

    override fun hashCode(): Int = close.hashCode()

    override fun toString(): String = "TabIcons(close=$close)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TabColors(
    public val background: Color,
    public val backgroundDisabled: Color,
    public val backgroundPressed: Color,
    public val backgroundHovered: Color,
    public val backgroundSelected: Color,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val contentSelected: Color,
    public val underline: Color,
    public val underlineDisabled: Color,
    public val underlinePressed: Color,
    public val underlineHovered: Color,
    public val underlineSelected: Color,
) {
    @Composable
    public fun contentFor(state: TabState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected -> contentSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = content,
                        disabled = contentDisabled,
                        pressed = contentPressed,
                        hovered = contentHovered,
                        active = content,
                    )
            }
        )

    @Composable
    public fun backgroundFor(state: TabState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                state.isActive -> background
                state.isSelected -> backgroundSelected
                else -> background
            }
        )

    @Composable
    public fun underlineFor(state: TabState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected -> underlineSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = underline,
                        disabled = underlineDisabled,
                        pressed = underlinePressed,
                        hovered = underlineHovered,
                        active = underline,
                    )
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabColors

        if (background != other.background) return false
        if (backgroundDisabled != other.backgroundDisabled) return false
        if (backgroundPressed != other.backgroundPressed) return false
        if (backgroundHovered != other.backgroundHovered) return false
        if (backgroundSelected != other.backgroundSelected) return false
        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentPressed != other.contentPressed) return false
        if (contentHovered != other.contentHovered) return false
        if (contentSelected != other.contentSelected) return false
        if (underline != other.underline) return false
        if (underlineDisabled != other.underlineDisabled) return false
        if (underlinePressed != other.underlinePressed) return false
        if (underlineHovered != other.underlineHovered) return false
        if (underlineSelected != other.underlineSelected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundDisabled.hashCode()
        result = 31 * result + backgroundPressed.hashCode()
        result = 31 * result + backgroundHovered.hashCode()
        result = 31 * result + backgroundSelected.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentPressed.hashCode()
        result = 31 * result + contentHovered.hashCode()
        result = 31 * result + contentSelected.hashCode()
        result = 31 * result + underline.hashCode()
        result = 31 * result + underlineDisabled.hashCode()
        result = 31 * result + underlinePressed.hashCode()
        result = 31 * result + underlineHovered.hashCode()
        result = 31 * result + underlineSelected.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabColors(" +
            "background=$background, " +
            "backgroundDisabled=$backgroundDisabled, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "backgroundSelected=$backgroundSelected, " +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "contentPressed=$contentPressed, " +
            "contentHovered=$contentHovered, " +
            "contentSelected=$contentSelected, " +
            "underline=$underline, " +
            "underlineDisabled=$underlineDisabled, " +
            "underlinePressed=$underlinePressed, " +
            "underlineHovered=$underlineHovered, " +
            "underlineSelected=$underlineSelected" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TabContentAlpha(
    public val iconNormal: Float,
    public val iconDisabled: Float,
    public val iconPressed: Float,
    public val iconHovered: Float,
    public val iconSelected: Float,
    public val contentNormal: Float,
    public val contentDisabled: Float,
    public val contentPressed: Float,
    public val contentHovered: Float,
    public val contentSelected: Float,
) {
    @Composable
    public fun iconFor(state: TabState): State<Float> =
        rememberUpdatedState(
            when {
                state.isSelected -> iconSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = iconNormal,
                        disabled = iconDisabled,
                        pressed = iconPressed,
                        hovered = iconHovered,
                        active = iconNormal,
                    )
            }
        )

    @Composable
    public fun contentFor(state: TabState): State<Float> =
        rememberUpdatedState(
            when {
                state.isSelected -> contentSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = contentNormal,
                        disabled = contentDisabled,
                        pressed = contentPressed,
                        hovered = contentHovered,
                        active = contentNormal,
                    )
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabContentAlpha

        if (iconNormal != other.iconNormal) return false
        if (iconDisabled != other.iconDisabled) return false
        if (iconPressed != other.iconPressed) return false
        if (iconHovered != other.iconHovered) return false
        if (iconSelected != other.iconSelected) return false
        if (contentNormal != other.contentNormal) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentPressed != other.contentPressed) return false
        if (contentHovered != other.contentHovered) return false
        if (contentSelected != other.contentSelected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = iconNormal.hashCode()
        result = 31 * result + iconDisabled.hashCode()
        result = 31 * result + iconPressed.hashCode()
        result = 31 * result + iconHovered.hashCode()
        result = 31 * result + iconSelected.hashCode()
        result = 31 * result + contentNormal.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentPressed.hashCode()
        result = 31 * result + contentHovered.hashCode()
        result = 31 * result + contentSelected.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabContentAlpha(" +
            "iconNormal=$iconNormal, " +
            "iconDisabled=$iconDisabled, " +
            "iconPressed=$iconPressed, " +
            "iconHovered=$iconHovered, " +
            "iconSelected=$iconSelected, " +
            "contentNormal=$contentNormal, " +
            "contentDisabled=$contentDisabled, " +
            "contentPressed=$contentPressed, " +
            "contentHovered=$contentHovered, " +
            "contentSelected=$contentSelected" +
            ")"
    }

    public companion object
}

// Tabs are the only components that handle hover states
@Composable
private fun <T> TabState.chooseValueIgnoreCompat(normal: T, disabled: T, pressed: T, hovered: T, active: T): T =
    when {
        !isEnabled -> disabled
        isPressed -> pressed
        isHovered -> hovered
        isActive -> active
        else -> normal
    }

public val LocalDefaultTabStyle: ProvidableCompositionLocal<TabStyle> = staticCompositionLocalOf {
    error("No LocalDefaultTabStyle provided. Have you forgotten the theme?")
}

public val LocalEditorTabStyle: ProvidableCompositionLocal<TabStyle> = staticCompositionLocalOf {
    error("No LocalEditorTabStyle provided. Have you forgotten the theme?")
}
