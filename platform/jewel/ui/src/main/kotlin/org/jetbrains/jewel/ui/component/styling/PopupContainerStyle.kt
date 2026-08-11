package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines color and size/spacing metrics for styling the popup container component. */
@Stable
@GenerateDataFunctions
public class PopupContainerStyle(
    /** Whether the popup container is using a dark color scheme. */
    public val isDark: Boolean,
    /** The color tokens for the popup container. */
    public val colors: PopupContainerColors,
    /** The size and spacing metrics for the popup container. */
    public val metrics: PopupContainerMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupContainerStyle

        if (isDark != other.isDark) return false
        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "PopupContainerStyle(isDark=$isDark, colors=$colors, metrics=$metrics)"

    /** Companion object for [PopupContainerStyle]. */
    public companion object
}

/** Holds color tokens for the popup container component, covering its background, border, and shadow. */
@Immutable
@GenerateDataFunctions
public class PopupContainerColors(
    /** The background color. */
    public val background: Color,
    /** The border color. */
    public val border: Color,
    /** The shadow color. */
    public val shadow: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupContainerColors

        if (background != other.background) return false
        if (border != other.border) return false
        if (shadow != other.shadow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }

    override fun toString(): String = "PopupContainerColors(background=$background, border=$border, shadow=$shadow)"

    /** Companion object for [PopupContainerColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the popup container component, including corner size, margins, padding, offset,
 * shadow size, and border width.
 */
@Stable
@GenerateDataFunctions
public class PopupContainerMetrics(
    /** The corner radius of the popup container. */
    public val cornerSize: CornerSize,
    /** The outer margin around the popup menu. */
    public val menuMargin: PaddingValues,
    /** The inner content padding of the popup container. */
    public val contentPadding: PaddingValues,
    /** The positional offset applied to the popup. */
    public val offset: DpOffset,
    /** The size of the drop shadow around the popup container. */
    public val shadowSize: Dp,
    /** The width of the popup container border. */
    public val borderWidth: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupContainerMetrics

        if (cornerSize != other.cornerSize) return false
        if (menuMargin != other.menuMargin) return false
        if (contentPadding != other.contentPadding) return false
        if (offset != other.offset) return false
        if (shadowSize != other.shadowSize) return false
        if (borderWidth != other.borderWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + menuMargin.hashCode()
        result = 31 * result + contentPadding.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + shadowSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        return result
    }

    override fun toString(): String {
        return "PopupContainerMetrics(" +
            "cornerSize=$cornerSize, " +
            "menuMargin=$menuMargin, " +
            "contentPadding=$contentPadding, " +
            "offset=$offset, " +
            "shadowSize=$shadowSize, " +
            "borderWidth=$borderWidth" +
            ")"
    }

    /** Companion object for [PopupContainerMetrics]. */
    public companion object
}

/** CompositionLocal providing the current [PopupContainerStyle]. */
public val LocalPopupContainerStyle: ProvidableCompositionLocal<PopupContainerStyle> = staticCompositionLocalOf {
    error("No PopupContainerStyle provided. Have you forgotten the theme?")
}
