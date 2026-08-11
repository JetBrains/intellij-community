package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/**
 * Combines styling for all severity variants (information, success, warning, error) of the default banner component.
 */
@Stable
@GenerateDataFunctions
public class DefaultBannerStyles(
    /** The style for the information severity variant. */
    public val information: DefaultBannerStyle,
    /** The style for the success severity variant. */
    public val success: DefaultBannerStyle,
    /** The style for the warning severity variant. */
    public val warning: DefaultBannerStyle,
    /** The style for the error severity variant. */
    public val error: DefaultBannerStyle,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultBannerStyles

        if (information != other.information) return false
        if (success != other.success) return false
        if (warning != other.warning) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = information.hashCode()
        result = 31 * result + success.hashCode()
        result = 31 * result + warning.hashCode()
        result = 31 * result + error.hashCode()
        return result
    }

    override fun toString(): String {
        return "DefaultBannerStyles(" +
            "information=$information, " +
            "success=$success, " +
            "warning=$warning, " +
            "error=$error" +
            ")"
    }

    /** Companion object for [DefaultBannerStyles]. */
    public companion object
}

/** Combines the colors and metrics that style a single-severity default banner component. */
@Stable
@GenerateDataFunctions
public class DefaultBannerStyle(
    /** The color tokens for the banner. */
    public val colors: BannerColors,
    /** The geometric and spacing properties for the banner. */
    public val metrics: BannerMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultBannerStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "DefaultBannerStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [DefaultBannerStyle]. */
    public companion object
}

/** Combines styling for all severity variants (information, success, warning, error) of the inline banner component. */
@Stable
@GenerateDataFunctions
public class InlineBannerStyles(
    /** The style for the information severity variant. */
    public val information: InlineBannerStyle,
    /** The style for the success severity variant. */
    public val success: InlineBannerStyle,
    /** The style for the warning severity variant. */
    public val warning: InlineBannerStyle,
    /** The style for the error severity variant. */
    public val error: InlineBannerStyle,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InlineBannerStyles

        if (information != other.information) return false
        if (success != other.success) return false
        if (warning != other.warning) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = information.hashCode()
        result = 31 * result + success.hashCode()
        result = 31 * result + warning.hashCode()
        result = 31 * result + error.hashCode()
        return result
    }

    override fun toString(): String {
        return "InlineBannerStyles(" +
            "information=$information, " +
            "success=$success, " +
            "warning=$warning, " +
            "error=$error" +
            ")"
    }

    /** Companion object for [InlineBannerStyles]. */
    public companion object
}

/** Combines the colors and metrics that style a single-severity inline banner component. */
@Stable
@GenerateDataFunctions
public class InlineBannerStyle(
    /** The color tokens for the banner. */
    public val colors: BannerColors,
    /** The geometric and spacing properties for the banner. */
    public val metrics: BannerMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InlineBannerStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "InlineBannerStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [InlineBannerStyle]. */
    public companion object
}

/** Holds color tokens for the banner component, covering background and border colors. */
@Immutable
@GenerateDataFunctions
public class BannerColors(
    /** The background color. */
    public val background: Color,
    /** The border color. */
    public val border: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BannerColors

        if (background != other.background) return false
        if (border != other.border) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + border.hashCode()
        return result
    }

    override fun toString(): String = "BannerColors(background=$background, border=$border)"

    /** Companion object for [BannerColors]. */
    public companion object
}

/**
 * Defines the geometric and spacing properties for a banner component.
 *
 * @param borderWidth The width of the banner's border stroke.
 * @param cornerSize The corner radius applied to the banner's shape.
 * @param padding The internal padding applied to the banner's content area.
 */
@Stable
@GenerateDataFunctions
public class BannerMetrics(
    /** The width of the banner's border stroke. */
    public val borderWidth: Dp,
    /** The corner radius applied to the banner's shape. */
    public val cornerSize: CornerSize,
    /** The internal padding applied to the banner's content area. */
    public val padding: PaddingValues,
) {
    @Deprecated(
        "Use the constructor with `cornerSize`  and `padding` parameters",
        replaceWith = ReplaceWith("BannerMetrics(borderWidth, cornerSize = TODO(), padding = TODO())"),
    )
    public constructor(borderWidth: Dp) : this(borderWidth, CornerSize(8.dp), PaddingValues(12.dp))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BannerMetrics

        if (borderWidth != other.borderWidth) return false
        if (cornerSize != other.cornerSize) return false
        if (padding != other.padding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borderWidth.hashCode()
        result = 31 * result + cornerSize.hashCode()
        result = 31 * result + padding.hashCode()
        return result
    }

    override fun toString(): String =
        "BannerMetrics(" + "borderWidth=$borderWidth, " + "cornerSize=$cornerSize, " + "padding=$padding" + ")"

    /** Companion object for [BannerMetrics]. */
    public companion object
}

/** CompositionLocal providing the [DefaultBannerStyles] for the current theme. */
public val LocalDefaultBannerStyle: ProvidableCompositionLocal<DefaultBannerStyles> = staticCompositionLocalOf {
    error("No DefaultBannerStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the [InlineBannerStyles] for the current theme. */
public val LocalInlineBannerStyle: ProvidableCompositionLocal<InlineBannerStyles> = staticCompositionLocalOf {
    error("No InlineBannerStyle provided. Have you forgotten the theme?")
}
