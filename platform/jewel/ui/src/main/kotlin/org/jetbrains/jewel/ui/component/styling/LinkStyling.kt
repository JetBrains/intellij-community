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

@Immutable
@GenerateDataFunctions
public class LinkStyle(
    public val colors: LinkColors,
    public val metrics: LinkMetrics,
    public val icons: LinkIcons,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LinkColors(
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val contentVisited: Color,
) {
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LinkMetrics(
    public val focusHaloCornerSize: CornerSize,
    public val textIconGap: Dp,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LinkIcons(public val dropdownChevron: IconKey, public val externalLink: IconKey) {
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

    public companion object
}

public val LocalLinkStyle: ProvidableCompositionLocal<LinkStyle> = staticCompositionLocalOf {
    error("No LinkStyle provided. Have you forgotten the theme?")
}

public enum class LinkUnderlineBehavior {
    ShowAlways,
    ShowOnHover,
}
