package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.BannerColors
import org.jetbrains.jewel.ui.component.styling.BannerMetrics
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyle
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyles

public val DefaultBannerStyles.Companion.Default: IntUiDefaultBannerStylesFactory
    get() = IntUiDefaultBannerStylesFactory

public object IntUiDefaultBannerStylesFactory {
    @Composable
    public fun light(
        information: DefaultBannerStyle = DefaultBannerStyle.Information.light(),
        success: DefaultBannerStyle = DefaultBannerStyle.Success.light(),
        warning: DefaultBannerStyle = DefaultBannerStyle.Warning.light(),
        error: DefaultBannerStyle = DefaultBannerStyle.Error.light(),
    ): DefaultBannerStyles =
        DefaultBannerStyles(information = information, success = success, warning = warning, error = error)

    @Composable
    public fun dark(
        information: DefaultBannerStyle = DefaultBannerStyle.Information.dark(),
        success: DefaultBannerStyle = DefaultBannerStyle.Success.dark(),
        warning: DefaultBannerStyle = DefaultBannerStyle.Warning.dark(),
        error: DefaultBannerStyle = DefaultBannerStyle.Error.dark(),
    ): DefaultBannerStyles =
        DefaultBannerStyles(information = information, success = success, warning = warning, error = error)
}

public fun BannerMetrics.Companion.default(borderWidth: Dp = 1.dp): BannerMetrics = BannerMetrics(borderWidth)

// region Information Banner
public val DefaultBannerStyle.Companion.Information: IntUiDefaultInformationBannerStyleFactory
    get() = IntUiDefaultInformationBannerStyleFactory

public object IntUiDefaultInformationBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Information.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Information.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

public val BannerColors.Companion.Information: IntUiDefaultInformationBannerColorFactory
    get() = IntUiDefaultInformationBannerColorFactory

public object IntUiDefaultInformationBannerColorFactory {
    @Composable
    public fun light(
        background: Color = IntUiLightTheme.colors.blue(13),
        border: Color = IntUiLightTheme.colors.blue(10),
    ): BannerColors = BannerColors(background = background, border = border)

    @Composable
    public fun dark(
        background: Color = IntUiDarkTheme.colors.blue(1),
        border: Color = IntUiDarkTheme.colors.blue(3),
    ): BannerColors = BannerColors(background = background, border = border)
}

// endregion

// region Success Banner
public val DefaultBannerStyle.Companion.Success: IntUiDefaultSuccessBannerStyleFactory
    get() = IntUiDefaultSuccessBannerStyleFactory

public object IntUiDefaultSuccessBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Success.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Success.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

public val BannerColors.Companion.Success: IntUiDefaultSuccessBannerColorFactory
    get() = IntUiDefaultSuccessBannerColorFactory

public object IntUiDefaultSuccessBannerColorFactory {
    @Composable
    public fun light(
        background: Color = IntUiLightTheme.colors.green(11),
        border: Color = IntUiLightTheme.colors.green(9),
    ): BannerColors = BannerColors(background = background, border = border)

    @Composable
    public fun dark(
        background: Color = IntUiDarkTheme.colors.green(1),
        border: Color = IntUiDarkTheme.colors.green(3),
    ): BannerColors = BannerColors(background = background, border = border)
}

// endregion

// region Warning Banner
public val DefaultBannerStyle.Companion.Warning: IntUiDefaultWarningBannerStyleFactory
    get() = IntUiDefaultWarningBannerStyleFactory

public object IntUiDefaultWarningBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Warning.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Warning.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

public val BannerColors.Companion.Warning: IntUiDefaultWarningBannerColorFactory
    get() = IntUiDefaultWarningBannerColorFactory

public object IntUiDefaultWarningBannerColorFactory {
    @Composable
    public fun light(
        background: Color = IntUiLightTheme.colors.yellow(10),
        border: Color = IntUiLightTheme.colors.yellow(6),
    ): BannerColors = BannerColors(background = background, border = border)

    @Composable
    public fun dark(
        background: Color = IntUiDarkTheme.colors.yellow(1),
        border: Color = IntUiDarkTheme.colors.yellow(2),
    ): BannerColors = BannerColors(background = background, border = border)
}

// endregion

// region Error Banner
public val DefaultBannerStyle.Companion.Error: IntUiDefaultErrorBannerStyleFactory
    get() = IntUiDefaultErrorBannerStyleFactory

public object IntUiDefaultErrorBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Error.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Error.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

public val BannerColors.Companion.Error: IntUiDefaultErrorBannerColorFactory
    get() = IntUiDefaultErrorBannerColorFactory

public object IntUiDefaultErrorBannerColorFactory {
    @Composable
    public fun light(
        background: Color = IntUiLightTheme.colors.red(12),
        border: Color = IntUiLightTheme.colors.red(9),
    ): BannerColors = BannerColors(background = background, border = border)

    @Composable
    public fun dark(
        background: Color = IntUiDarkTheme.colors.red(1),
        border: Color = IntUiDarkTheme.colors.red(3),
    ): BannerColors = BannerColors(background = background, border = border)
}
// endregion
