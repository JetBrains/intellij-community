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
import org.jetbrains.jewel.ui.component.styling.InlineBannerStyle
import org.jetbrains.jewel.ui.component.styling.InlineBannerStyles

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

public val BannerColors.Companion.Information: IntUiInformationBannerColorFactory
    get() = IntUiInformationBannerColorFactory

public object IntUiInformationBannerColorFactory {
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

public object IntUiSuccessBannerColorFactory {
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

public val BannerColors.Companion.Warning: IntUiWarningBannerColorFactory
    get() = IntUiWarningBannerColorFactory

public object IntUiWarningBannerColorFactory {
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

public val BannerColors.Companion.Error: IntUiErrorBannerColorFactory
    get() = IntUiErrorBannerColorFactory

public object IntUiErrorBannerColorFactory {
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

public val InlineBannerStyles.Companion.Default: IntUiInlineBannerStylesFactory
    get() = IntUiInlineBannerStylesFactory

public object IntUiInlineBannerStylesFactory {
    @Composable
    public fun light(
        information: InlineBannerStyle = InlineBannerStyle.Information.light(),
        success: InlineBannerStyle = InlineBannerStyle.Success.light(),
        warning: InlineBannerStyle = InlineBannerStyle.Warning.light(),
        error: InlineBannerStyle = InlineBannerStyle.Error.light(),
    ): InlineBannerStyles =
        InlineBannerStyles(information = information, success = success, warning = warning, error = error)

    @Composable
    public fun dark(
        information: InlineBannerStyle = InlineBannerStyle.Information.dark(),
        success: InlineBannerStyle = InlineBannerStyle.Success.dark(),
        warning: InlineBannerStyle = InlineBannerStyle.Warning.dark(),
        error: InlineBannerStyle = InlineBannerStyle.Error.dark(),
    ): InlineBannerStyles =
        InlineBannerStyles(information = information, success = success, warning = warning, error = error)
}

public fun BannerMetrics.Companion.default(borderWidth: Dp = 1.dp): BannerMetrics = BannerMetrics(borderWidth)

// region Inline Information Banner
public val InlineBannerStyle.Companion.Information: IntUiInlineInformationBannerStyleFactory
    get() = IntUiInlineInformationBannerStyleFactory

public object IntUiInlineInformationBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Information.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Information.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}

// endregion

// region Inline Success Banner
public val InlineBannerStyle.Companion.Success: IntUiInlineSuccessBannerStyleFactory
    get() = IntUiInlineSuccessBannerStyleFactory

public object IntUiInlineSuccessBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Success.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Success.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}

public val BannerColors.Companion.Success: IntUiSuccessBannerColorFactory
    get() = IntUiSuccessBannerColorFactory

// endregion

// region Inline Warning Banner
public val InlineBannerStyle.Companion.Warning: IntUiInlineWarningBannerStyleFactory
    get() = IntUiInlineWarningBannerStyleFactory

public object IntUiInlineWarningBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Warning.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Warning.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}

// endregion

// region Inline Error Banner
public val InlineBannerStyle.Companion.Error: IntUiInlineErrorBannerStyleFactory
    get() = IntUiInlineErrorBannerStyleFactory

public object IntUiInlineErrorBannerStyleFactory {
    @Composable
    public fun light(
        colors: BannerColors = BannerColors.Error.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    @Composable
    public fun dark(
        colors: BannerColors = BannerColors.Error.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}
// endregion
