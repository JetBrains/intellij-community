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

/** Combines colors, metrics, icons, content alpha, and scrollbar style for a tab component. */
@Stable
@GenerateDataFunctions
public class TabStyle(
    /** The color tokens for the tab component. */
    public val colors: TabColors,
    /** The size and spacing metrics for the tab component. */
    public val metrics: TabMetrics,
    /** The icon keys for the tab component. */
    public val icons: TabIcons,
    /** The content alpha values for the tab component. */
    public val contentAlpha: TabContentAlpha,
    /** The scrollbar style used within the tab strip. */
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

    /** Companion object for [TabStyle]. */
    public companion object
}

/** Holds size and spacing metrics for the tab component. */
@Stable
@GenerateDataFunctions
public class TabMetrics(
    /** The thickness of the selected tab's underline indicator. */
    public val underlineThickness: Dp,
    /** The padding applied inside each tab. */
    public val tabPadding: PaddingValues,
    /** The height of a tab. */
    public val tabHeight: Dp,
    /** The spacing between elements inside a tab's content area. */
    public val tabContentSpacing: Dp,
    /** The gap between the close button and the tab content. */
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

    /** Companion object for [TabMetrics]. */
    public companion object
}

/** Holds the icon key for the tab component's close button. */
@Immutable
@GenerateDataFunctions
public class TabIcons(
    /** The icon key for the tab's close button. */
    public val close: IconKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabIcons

        return close == other.close
    }

    override fun hashCode(): Int = close.hashCode()

    override fun toString(): String = "TabIcons(close=$close)"

    /** Companion object for [TabIcons]. */
    public companion object
}

/**
 * Holds color tokens for the tab component in its various states, including background, content, and underline colors.
 */
@Immutable
@GenerateDataFunctions
public class TabColors(
    /** The background color in the normal state. */
    public val background: Color,
    /** The background color when the tab is disabled. */
    public val backgroundDisabled: Color,
    /** The background color when the tab is pressed. */
    public val backgroundPressed: Color,
    /** The background color when the tab is hovered. */
    public val backgroundHovered: Color,
    /** The background color when the tab is selected. */
    public val backgroundSelected: Color,
    /** The content (text/icon) color in the normal state. */
    public val content: Color,
    /** The content color when the tab is disabled. */
    public val contentDisabled: Color,
    /** The content color when the tab is pressed. */
    public val contentPressed: Color,
    /** The content color when the tab is hovered. */
    public val contentHovered: Color,
    /** The content color when the tab is selected. */
    public val contentSelected: Color,
    /** The underline indicator color in the normal state. */
    public val underline: Color,
    /** The underline indicator color when the tab is disabled. */
    public val underlineDisabled: Color,
    /** The underline indicator color when the tab is pressed. */
    public val underlinePressed: Color,
    /** The underline indicator color when the tab is hovered. */
    public val underlineHovered: Color,
    /** The underline indicator color when the tab is selected. */
    public val underlineSelected: Color,
) {
    /**
     * Returns a [State] holding the content color appropriate for the given [state].
     *
     * @param state The current [TabState].
     */
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

    /**
     * Returns a [State] holding the background color appropriate for the given [state].
     *
     * @param state The current [TabState].
     */
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

    /**
     * Returns a [State] holding the underline color appropriate for the given [state].
     *
     * @param state The current [TabState].
     */
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

    /** Companion object for [TabColors]. */
    public companion object
}

/** Holds alpha values for icon and content in the tab component across its various states. */
@Immutable
@GenerateDataFunctions
public class TabContentAlpha(
    /** The icon opacity in the normal state. */
    public val iconNormal: Float,
    /** The icon opacity when the tab is disabled. */
    public val iconDisabled: Float,
    /** The icon opacity when the tab is pressed. */
    public val iconPressed: Float,
    /** The icon opacity when the tab is hovered. */
    public val iconHovered: Float,
    /** The icon opacity when the tab is selected. */
    public val iconSelected: Float,
    /** The content opacity in the normal state. */
    public val contentNormal: Float,
    /** The content opacity when the tab is disabled. */
    public val contentDisabled: Float,
    /** The content opacity when the tab is pressed. */
    public val contentPressed: Float,
    /** The content opacity when the tab is hovered. */
    public val contentHovered: Float,
    /** The content opacity when the tab is selected. */
    public val contentSelected: Float,
) {
    /**
     * Returns a [State] holding the icon opacity appropriate for the given [state].
     *
     * @param state The current [TabState].
     */
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

    /**
     * Returns a [State] holding the content opacity appropriate for the given [state].
     *
     * @param state The current [TabState].
     */
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

    /** Companion object for [TabContentAlpha]. */
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

/** CompositionLocal providing the default [TabStyle] for non-editor tabs. */
public val LocalDefaultTabStyle: ProvidableCompositionLocal<TabStyle> = staticCompositionLocalOf {
    error("No LocalDefaultTabStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the [TabStyle] for editor tabs. */
public val LocalEditorTabStyle: ProvidableCompositionLocal<TabStyle> = staticCompositionLocalOf {
    error("No LocalEditorTabStyle provided. Have you forgotten the theme?")
}
