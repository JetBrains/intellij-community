package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Stable
@GenerateDataFunctions
public class DefaultBannerStyles(
    public val information: DefaultBannerStyle,
    public val success: DefaultBannerStyle,
    public val warning: DefaultBannerStyle,
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class DefaultBannerStyle(public val colors: BannerColors, public val metrics: BannerMetrics) {
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class InlineBannerStyles(
    public val information: InlineBannerStyle,
    public val success: InlineBannerStyle,
    public val warning: InlineBannerStyle,
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class InlineBannerStyle(public val colors: BannerColors, public val metrics: BannerMetrics) {
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class BannerColors(public val background: Color, public val border: Color) {
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class BannerMetrics(public val borderWidth: Dp) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BannerMetrics

        return borderWidth == other.borderWidth
    }

    override fun hashCode(): Int = borderWidth.hashCode()

    override fun toString(): String = "BannerMetrics(borderWidth=$borderWidth)"

    public companion object
}

public val LocalDefaultBannerStyle: ProvidableCompositionLocal<DefaultBannerStyles> = staticCompositionLocalOf {
    error("No DefaultBannerStyle provided. Have you forgotten the theme?")
}

public val LocalInlineBannerStyle: ProvidableCompositionLocal<InlineBannerStyles> = staticCompositionLocalOf {
    error("No InlineBannerStyle provided. Have you forgotten the theme?")
}
