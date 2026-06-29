package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.LinkState
import org.jetbrains.jewel.ui.icon.IconKey

/** Combines [LinkColors], [LinkMetrics], [LinkIcons], and underline behavior for styling a Link component. */
@Immutable
@GenerateDataFunctions
public class LinkStyle(
    /** The color tokens for the link. */
    public val colors: LinkColors,
    /** The size and spacing metrics for the link. */
    public val metrics: LinkMetrics,
    /** The icon keys for the link. */
    public val icons: LinkIcons,
    /** The underline behavior for the link. */
    public val underlineBehavior: LinkUnderlineBehavior,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinkStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false
        if (underlineBehavior != other.underlineBehavior) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        result = 31 * result + underlineBehavior.hashCode()
        return result
    }

    override fun toString(): String {
        return "LinkStyle(" +
            "colors=$colors, " +
            "metrics=$metrics, " +
            "icons=$icons, " +
            "underlineBehavior=$underlineBehavior" +
            ")"
    }

    /** Companion object for [LinkStyle]. */
    public companion object
}

/** Holds color tokens for the Link component in its various states, including visited and disabled. */
@Immutable
@GenerateDataFunctions
public class LinkColors(
    /** The content color in the normal state. */
    public val content: Color,
    /** The content color when the link is disabled. */
    public val contentDisabled: Color,
    /** The content color when the link is focused. */
    public val contentFocused: Color,
    /** The content color when the link is pressed. */
    public val contentPressed: Color,
    /** The content color when the link is hovered. */
    public val contentHovered: Color,
    /** The content color when the link has been visited. */
    public val contentVisited: Color,
) {
    /**
     * Returns a [State] holding the content color appropriate for the given [state], including visited and disabled
     * variants.
     */
    @Composable
    public fun contentFor(state: LinkState): State<Color> =
        rememberUpdatedState(
            state.chooseValueWithVisited(
                normal = content,
                disabled = contentDisabled,
                focused = contentFocused,
                pressed = contentPressed,
                hovered = contentHovered,
                visited = contentVisited,
                active = content,
            )
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinkColors

        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentFocused != other.contentFocused) return false
        if (contentPressed != other.contentPressed) return false
        if (contentHovered != other.contentHovered) return false
        if (contentVisited != other.contentVisited) return false

        return true
    }

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentFocused.hashCode()
        result = 31 * result + contentPressed.hashCode()
        result = 31 * result + contentHovered.hashCode()
        result = 31 * result + contentVisited.hashCode()
        return result
    }

    override fun toString(): String {
        return "LinkColors(" +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "contentFocused=$contentFocused, " +
            "contentPressed=$contentPressed, " +
            "contentHovered=$contentHovered, " +
            "contentVisited=$contentVisited" +
            ")"
    }

    /** Companion object for [LinkColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the Link component, including focus halo corner size, text-icon gap, and icon
 * size.
 */
@Immutable
@GenerateDataFunctions
public class LinkMetrics(
    /** The corner size of the focus halo. */
    public val focusHaloCornerSize: CornerSize,
    /** The gap between the link text and its icon. */
    public val textIconGap: Dp,
    /** The size of the link icon. */
    public val iconSize: DpSize,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinkMetrics

        if (focusHaloCornerSize != other.focusHaloCornerSize) return false
        if (textIconGap != other.textIconGap) return false
        if (iconSize != other.iconSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = focusHaloCornerSize.hashCode()
        result = 31 * result + textIconGap.hashCode()
        result = 31 * result + iconSize.hashCode()
        return result
    }

    override fun toString(): String {
        return "LinkMetrics(" +
            "focusHaloCornerSize=$focusHaloCornerSize, " +
            "textIconGap=$textIconGap, " +
            "iconSize=$iconSize" +
            ")"
    }

    /** Companion object for [LinkMetrics]. */
    public companion object
}

/** Holds icon keys for the Link component, providing the dropdown chevron and external link icons. */
@Immutable
@GenerateDataFunctions
public class LinkIcons(
    /** The icon key for the dropdown chevron. */
    public val dropdownChevron: IconKey,
    /** The icon key for external link indicators. */
    public val externalLink: IconKey,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinkIcons

        if (dropdownChevron != other.dropdownChevron) return false
        if (externalLink != other.externalLink) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dropdownChevron.hashCode()
        result = 31 * result + externalLink.hashCode()
        return result
    }

    override fun toString(): String = "LinkIcons(dropdownChevron=$dropdownChevron, externalLink=$externalLink)"

    /** Companion object for [LinkIcons]. */
    public companion object
}

/** CompositionLocal used to provide the [LinkStyle] to link components. */
public val LocalLinkStyle: ProvidableCompositionLocal<LinkStyle> = staticCompositionLocalOf {
    error("No LinkStyle provided. Have you forgotten the theme?")
}

/** Controls when the underline decoration is shown on a Link component. */
public enum class LinkUnderlineBehavior {
    /** The underline is always visible. */
    ShowAlways,

    /** The underline is only visible when the link is hovered. */
    ShowOnHover,
}
