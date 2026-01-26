package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.BadgeState

/**
 * Contains the complete set of [BadgeStyle]s available in the theme, one per badge variant.
 *
 * Obtain the current instance via [org.jetbrains.jewel.ui.theme.badgeStyle].
 *
 * @property blue Style for the primary blue badge (e.g. "New").
 * @property blueSecondary Style for the default/secondary blue badge (e.g. a generic informational label).
 * @property green Style for the primary green badge (e.g. "Free").
 * @property greenSecondary Style for the secondary green badge (e.g. "Trial").
 * @property purpleSecondary Style for the secondary purple badge (e.g. "Beta").
 * @property graySecondary Style for the secondary gray badge (e.g. "Information").
 */
@Stable
@GenerateDataFunctions
public class BadgeStyles(
    public val blue: BadgeStyle,
    public val blueSecondary: BadgeStyle,
    public val green: BadgeStyle,
    public val greenSecondary: BadgeStyle,
    public val purpleSecondary: BadgeStyle,
    public val graySecondary: BadgeStyle,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BadgeStyles

        if (blueSecondary != other.blueSecondary) return false
        if (blue != other.blue) return false
        if (green != other.green) return false
        if (greenSecondary != other.greenSecondary) return false
        if (purpleSecondary != other.purpleSecondary) return false
        if (graySecondary != other.graySecondary) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blueSecondary.hashCode()
        result = 31 * result + blue.hashCode()
        result = 31 * result + green.hashCode()
        result = 31 * result + greenSecondary.hashCode()
        result = 31 * result + purpleSecondary.hashCode()
        result = 31 * result + graySecondary.hashCode()
        return result
    }

    override fun toString(): String =
        "BadgeStyles(" +
            "blueSecondary=$blueSecondary, " +
            "blue=$blue, " +
            "green=$green, " +
            "greenSecondary=$greenSecondary, " +
            "purpleSecondary=$purpleSecondary, " +
            "graySecondary=$graySecondary" +
            ")"

    public companion object
}

/**
 * Defines the visual appearance of a [org.jetbrains.jewel.ui.component.Badge].
 *
 * @property colors The color palette for each interactive state.
 * @property metrics The sizing and shape metrics.
 */
@Stable
@GenerateDataFunctions
public class BadgeStyle(public val colors: BadgeColors, public val metrics: BadgeMetrics) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BadgeStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "BadgeStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

/**
 * Defines the background and content colors for a [org.jetbrains.jewel.ui.component.Badge] across all interactive
 * states.
 *
 * @property background Background brush in the default state.
 * @property backgroundDisabled Background brush when the badge is disabled.
 * @property backgroundFocused Background brush when the badge is focused.
 * @property backgroundPressed Background brush when the badge is pressed.
 * @property backgroundHovered Background brush when the badge is hovered.
 * @property content Content (foreground) color in the default state.
 * @property contentDisabled Content color when the badge is disabled.
 * @property contentFocused Content color when the badge is focused.
 * @property contentPressed Content color when the badge is pressed.
 * @property contentHovered Content color when the badge is hovered.
 */
@Stable
@GenerateDataFunctions
public class BadgeColors(
    public val background: Brush,
    public val backgroundDisabled: Brush,
    public val backgroundFocused: Brush,
    public val backgroundPressed: Brush,
    public val backgroundHovered: Brush,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
) {
    /**
     * Returns the appropriate background brush for the given badge state.
     *
     * State precedence (highest to lowest):
     * 1. Disabled
     * 2. Pressed
     * 3. Focused
     * 4. Hovered
     * 5. Default
     *
     * @param state The current badge state.
     * @return A State containing the appropriate background brush.
     */
    @Composable
    public fun backgroundFor(state: BadgeState): State<Brush> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isPressed -> backgroundPressed
                state.isFocused -> backgroundFocused
                state.isHovered -> backgroundHovered
                else -> background
            }
        )

    /**
     * Returns the appropriate content color for the given badge state.
     *
     * State precedence (highest to lowest):
     * 1. Disabled
     * 2. Pressed
     * 3. Focused
     * 4. Hovered
     * 5. Default
     *
     * @param state The current badge state.
     * @return A State containing the appropriate content color.
     */
    @Composable
    public fun contentFor(state: BadgeState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> contentDisabled
                state.isPressed -> contentPressed
                state.isFocused -> contentFocused
                state.isHovered -> contentHovered
                else -> content
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BadgeColors

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
        return result
    }

    override fun toString(): String =
        "BadgeColors(" +
            "background=$background, " +
            "backgroundDisabled=$backgroundDisabled, " +
            "backgroundFocused=$backgroundFocused, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "contentFocused=$contentFocused, " +
            "contentPressed=$contentPressed, " +
            "contentHovered=$contentHovered" +
            ")"

    public companion object
}

/**
 * Defines the sizing and shape metrics for a [org.jetbrains.jewel.ui.component.Badge].
 *
 * @property cornerSize The corner radius of the badge's rounded rectangle shape. Defaults to fully pill-shaped.
 * @property padding The internal padding applied to the badge content.
 * @property minHeight The minimum height of the badge. The badge grows taller if its content requires more space.
 */
@Stable
@GenerateDataFunctions
public class BadgeMetrics(
    public val cornerSize: CornerSize,
    public val padding: PaddingValues,
    public val minHeight: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BadgeMetrics

        if (cornerSize != other.cornerSize) return false
        if (padding != other.padding) return false
        if (minHeight != other.minHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + minHeight.hashCode()
        return result
    }

    override fun toString(): String = "BadgeMetrics(cornerSize=$cornerSize, padding=$padding, minHeight=$minHeight)"

    public companion object
}

public val LocalBadgeStyle: ProvidableCompositionLocal<BadgeStyles> = staticCompositionLocalOf {
    error("No BadgeStyle provided. Have you forgotten the theme?")
}

/** Creating a fallback style for compatibility with older versions. */
internal fun fallbackBadgeStyle(): BadgeStyles {
    val metrics =
        BadgeMetrics(cornerSize = CornerSize(100), padding = PaddingValues(horizontal = 6.dp), minHeight = 16.dp)

    val disabledBackground = SolidColor(Color(0xFF6C707E).copy(alpha = .12f))
    val disabledContent = Color(0xFFA8ADBD)

    val defaultColors =
        BadgeColors(
            background = SolidColor(Color(0xFF3574F0).copy(alpha = .16f)),
            backgroundDisabled = disabledBackground,
            backgroundFocused = SolidColor(Color(0xFF3574F0).copy(alpha = .16f)),
            backgroundPressed = SolidColor(Color(0xFF3574F0).copy(alpha = .16f)),
            backgroundHovered = SolidColor(Color(0xFF3574F0).copy(alpha = .16f)),
            content = Color(0xFF2E55A3),
            contentDisabled = disabledContent,
            contentFocused = Color(0xFF2E55A3),
            contentPressed = Color(0xFF2E55A3),
            contentHovered = Color(0xFF2E55A3),
        )

    val newColors =
        BadgeColors(
            background = SolidColor(Color(0xFF3574F0)),
            backgroundDisabled = disabledBackground,
            backgroundFocused = SolidColor(Color(0xFF3574F0)),
            backgroundPressed = SolidColor(Color(0xFF3574F0)),
            backgroundHovered = SolidColor(Color(0xFF3574F0)),
            content = Color(0xFFFFFFFF),
            contentDisabled = disabledContent,
            contentFocused = Color(0xFFFFFFFF),
            contentPressed = Color(0xFFFFFFFF),
            contentHovered = Color(0xFFFFFFFF),
        )

    val betaColors =
        BadgeColors(
            background = SolidColor(Color(0xFF834DF0).copy(alpha = .16f)),
            backgroundDisabled = disabledBackground,
            backgroundFocused = SolidColor(Color(0xFF834DF0).copy(alpha = .16f)),
            backgroundPressed = SolidColor(Color(0xFF834DF0).copy(alpha = .16f)),
            backgroundHovered = SolidColor(Color(0xFF834DF0).copy(alpha = .16f)),
            content = Color(0xFF55339C),
            contentDisabled = disabledContent,
            contentFocused = Color(0xFF55339C),
            contentPressed = Color(0xFF55339C),
            contentHovered = Color(0xFF55339C),
        )

    val freeColors =
        BadgeColors(
            background = SolidColor(Color(0xFF208A3C)),
            backgroundDisabled = disabledBackground,
            backgroundFocused = SolidColor(Color(0xFF208A3C)),
            backgroundPressed = SolidColor(Color(0xFF208A3C)),
            backgroundHovered = SolidColor(Color(0xFF208A3C)),
            content = Color(0xFFFFFFFF),
            contentDisabled = disabledContent,
            contentFocused = Color(0xFFFFFFFF),
            contentPressed = Color(0xFFFFFFFF),
            contentHovered = Color(0xFFFFFFFF),
        )

    val trialColors =
        BadgeColors(
            background = SolidColor(Color(0xFF208A3C).copy(alpha = .16f)),
            backgroundDisabled = disabledBackground,
            backgroundFocused = SolidColor(Color(0xFF208A3C).copy(alpha = .16f)),
            backgroundPressed = SolidColor(Color(0xFF208A3C).copy(alpha = .16f)),
            backgroundHovered = SolidColor(Color(0xFF208A3C).copy(alpha = .16f)),
            content = Color(0xFF1E6B33),
            contentDisabled = disabledContent,
            contentFocused = Color(0xFF1E6B33),
            contentPressed = Color(0xFF1E6B33),
            contentHovered = Color(0xFF1E6B33),
        )

    val informationColors =
        BadgeColors(
            background = SolidColor(Color(0xFF6C707E).copy(alpha = .12f)),
            backgroundDisabled = disabledBackground,
            backgroundFocused = SolidColor(Color(0xFF6C707E).copy(alpha = .12f)),
            backgroundPressed = SolidColor(Color(0xFF6C707E).copy(alpha = .12f)),
            backgroundHovered = SolidColor(Color(0xFF6C707E).copy(alpha = .12f)),
            content = Color(0xFF6C707E),
            contentDisabled = disabledContent,
            contentFocused = Color(0xFF6C707E),
            contentPressed = Color(0xFF6C707E),
            contentHovered = Color(0xFF6C707E),
        )

    return BadgeStyles(
        blueSecondary = BadgeStyle(colors = defaultColors, metrics = metrics),
        blue = BadgeStyle(colors = newColors, metrics = metrics),
        purpleSecondary = BadgeStyle(colors = betaColors, metrics = metrics),
        green = BadgeStyle(colors = freeColors, metrics = metrics),
        greenSecondary = BadgeStyle(colors = trialColors, metrics = metrics),
        graySecondary = BadgeStyle(colors = informationColors, metrics = metrics),
    )
}
