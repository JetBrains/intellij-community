package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
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

/** Provides access to the [IntUiDefaultBannerStylesFactory] via the [DefaultBannerStyles] companion. */
public val DefaultBannerStyles.Companion.Default: IntUiDefaultBannerStylesFactory
    get() = IntUiDefaultBannerStylesFactory

/** Factory object for creating Int UI [DefaultBannerStyles] instances. */
public object IntUiDefaultBannerStylesFactory {
    /** Creates an Int UI light [DefaultBannerStyles] with the provided parameters. */
    public fun light(
        information: DefaultBannerStyle = DefaultBannerStyle.Information.light(),
        success: DefaultBannerStyle = DefaultBannerStyle.Success.light(),
        warning: DefaultBannerStyle = DefaultBannerStyle.Warning.light(),
        error: DefaultBannerStyle = DefaultBannerStyle.Error.light(),
    ): DefaultBannerStyles =
        DefaultBannerStyles(information = information, success = success, warning = warning, error = error)

    /** Creates an Int UI dark [DefaultBannerStyles] with the provided parameters. */
    public fun dark(
        information: DefaultBannerStyle = DefaultBannerStyle.Information.dark(),
        success: DefaultBannerStyle = DefaultBannerStyle.Success.dark(),
        warning: DefaultBannerStyle = DefaultBannerStyle.Warning.dark(),
        error: DefaultBannerStyle = DefaultBannerStyle.Error.dark(),
    ): DefaultBannerStyles =
        DefaultBannerStyles(information = information, success = success, warning = warning, error = error)
}

/** Provides access to the [IntUiDefaultInformationBannerStyleFactory] via the [DefaultBannerStyle] companion. */
public val DefaultBannerStyle.Companion.Information: IntUiDefaultInformationBannerStyleFactory
    get() = IntUiDefaultInformationBannerStyleFactory

/** Factory object for creating Int UI information-variant [DefaultBannerStyle] instances. */
public object IntUiDefaultInformationBannerStyleFactory {
    /** Creates an Int UI light information [DefaultBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Information.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark information [DefaultBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Information.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

/** Provides access to the [IntUiInformationBannerColorFactory] via the [BannerColors] companion. */
public val BannerColors.Companion.Information: IntUiInformationBannerColorFactory
    get() = IntUiInformationBannerColorFactory

/** Factory object for creating Int UI information-variant [BannerColors] instances. */
public object IntUiInformationBannerColorFactory {
    /** Creates an Int UI light information [BannerColors] with the provided parameters. */
    public fun light(
        background: Color = IntUiLightTheme.colors.blue(13),
        border: Color = IntUiLightTheme.colors.blue(10),
    ): BannerColors = BannerColors(background = background, border = border)

    /** Creates an Int UI dark information [BannerColors] with the provided parameters. */
    public fun dark(
        background: Color = IntUiDarkTheme.colors.blue(1),
        border: Color = IntUiDarkTheme.colors.blue(3),
    ): BannerColors = BannerColors(background = background, border = border)
}

/** Provides access to the [IntUiDefaultSuccessBannerStyleFactory] via the [DefaultBannerStyle] companion. */
public val DefaultBannerStyle.Companion.Success: IntUiDefaultSuccessBannerStyleFactory
    get() = IntUiDefaultSuccessBannerStyleFactory

/** Factory object for creating Int UI success-variant [DefaultBannerStyle] instances. */
public object IntUiDefaultSuccessBannerStyleFactory {
    /** Creates an Int UI light success [DefaultBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Success.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark success [DefaultBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Success.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

/** Factory object for creating Int UI success-variant [BannerColors] instances. */
public object IntUiSuccessBannerColorFactory {
    /** Creates an Int UI light success [BannerColors] with the provided parameters. */
    public fun light(
        background: Color = IntUiLightTheme.colors.green(11),
        border: Color = IntUiLightTheme.colors.green(9),
    ): BannerColors = BannerColors(background = background, border = border)

    /** Creates an Int UI dark success [BannerColors] with the provided parameters. */
    public fun dark(
        background: Color = IntUiDarkTheme.colors.green(1),
        border: Color = IntUiDarkTheme.colors.green(3),
    ): BannerColors = BannerColors(background = background, border = border)
}

/** Provides access to the [IntUiDefaultWarningBannerStyleFactory] via the [DefaultBannerStyle] companion. */
public val DefaultBannerStyle.Companion.Warning: IntUiDefaultWarningBannerStyleFactory
    get() = IntUiDefaultWarningBannerStyleFactory

/** Factory object for creating Int UI warning-variant [DefaultBannerStyle] instances. */
public object IntUiDefaultWarningBannerStyleFactory {
    /** Creates an Int UI light warning [DefaultBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Warning.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark warning [DefaultBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Warning.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

/** Provides access to the [IntUiWarningBannerColorFactory] via the [BannerColors] companion. */
public val BannerColors.Companion.Warning: IntUiWarningBannerColorFactory
    get() = IntUiWarningBannerColorFactory

/** Factory object for creating Int UI warning-variant [BannerColors] instances. */
public object IntUiWarningBannerColorFactory {
    /** Creates an Int UI light warning [BannerColors] with the provided parameters. */
    public fun light(
        background: Color = IntUiLightTheme.colors.yellow(10),
        border: Color = IntUiLightTheme.colors.yellow(6),
    ): BannerColors = BannerColors(background = background, border = border)

    /** Creates an Int UI dark warning [BannerColors] with the provided parameters. */
    public fun dark(
        background: Color = IntUiDarkTheme.colors.yellow(1),
        border: Color = IntUiDarkTheme.colors.yellow(2),
    ): BannerColors = BannerColors(background = background, border = border)
}

/** Provides access to the [IntUiDefaultErrorBannerStyleFactory] via the [DefaultBannerStyle] companion. */
public val DefaultBannerStyle.Companion.Error: IntUiDefaultErrorBannerStyleFactory
    get() = IntUiDefaultErrorBannerStyleFactory

/** Factory object for creating Int UI error-variant [DefaultBannerStyle] instances. */
public object IntUiDefaultErrorBannerStyleFactory {
    /** Creates an Int UI light error [DefaultBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Error.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark error [DefaultBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Error.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): DefaultBannerStyle = DefaultBannerStyle(colors = colors, metrics = metrics)
}

/** Provides access to the [IntUiErrorBannerColorFactory] via the [BannerColors] companion. */
public val BannerColors.Companion.Error: IntUiErrorBannerColorFactory
    get() = IntUiErrorBannerColorFactory

/** Factory object for creating Int UI error-variant [BannerColors] instances. */
public object IntUiErrorBannerColorFactory {
    /** Creates an Int UI light error [BannerColors] with the provided parameters. */
    public fun light(
        background: Color = IntUiLightTheme.colors.red(12),
        border: Color = IntUiLightTheme.colors.red(9),
    ): BannerColors = BannerColors(background = background, border = border)

    /** Creates an Int UI dark error [BannerColors] with the provided parameters. */
    public fun dark(
        background: Color = IntUiDarkTheme.colors.red(1),
        border: Color = IntUiDarkTheme.colors.red(3),
    ): BannerColors = BannerColors(background = background, border = border)
}

// endregion

/** Provides access to the [IntUiInlineBannerStylesFactory] via the [InlineBannerStyles] companion. */
public val InlineBannerStyles.Companion.Default: IntUiInlineBannerStylesFactory
    get() = IntUiInlineBannerStylesFactory

/** Factory object for creating Int UI [InlineBannerStyles] instances. */
public object IntUiInlineBannerStylesFactory {
    /** Creates an Int UI light [InlineBannerStyles] with the provided parameters. */
    public fun light(
        information: InlineBannerStyle = InlineBannerStyle.Information.light(),
        success: InlineBannerStyle = InlineBannerStyle.Success.light(),
        warning: InlineBannerStyle = InlineBannerStyle.Warning.light(),
        error: InlineBannerStyle = InlineBannerStyle.Error.light(),
    ): InlineBannerStyles =
        InlineBannerStyles(information = information, success = success, warning = warning, error = error)

    /** Creates an Int UI dark [InlineBannerStyles] with the provided parameters. */
    public fun dark(
        information: InlineBannerStyle = InlineBannerStyle.Information.dark(),
        success: InlineBannerStyle = InlineBannerStyle.Success.dark(),
        warning: InlineBannerStyle = InlineBannerStyle.Warning.dark(),
        error: InlineBannerStyle = InlineBannerStyle.Error.dark(),
    ): InlineBannerStyles =
        InlineBannerStyles(information = information, success = success, warning = warning, error = error)
}

/** Creates an Int UI default [BannerMetrics] with the provided parameters. */
@Deprecated(
    "Use the `default()` function with `cornerSize` and `paddingValues`",
    replaceWith = ReplaceWith("default(borderWidth, cornerSize = TODO(), paddingValues = TODO())"),
)
public fun BannerMetrics.Companion.default(borderWidth: Dp = 1.dp): BannerMetrics =
    BannerMetrics(borderWidth, CornerSize(8.dp), PaddingValues(12.dp))

/** Creates an Int UI default [BannerMetrics] with the provided parameters. */
public fun BannerMetrics.Companion.default(
    borderWidth: Dp = 1.dp,
    cornerSize: CornerSize = CornerSize(8.dp),
    paddingValues: PaddingValues = PaddingValues(12.dp),
): BannerMetrics = BannerMetrics(borderWidth, cornerSize, paddingValues)

/** Provides access to the [IntUiInlineInformationBannerStyleFactory] via the [InlineBannerStyle] companion. */
public val InlineBannerStyle.Companion.Information: IntUiInlineInformationBannerStyleFactory
    get() = IntUiInlineInformationBannerStyleFactory

/** Factory object for creating Int UI information-variant [InlineBannerStyle] instances. */
public object IntUiInlineInformationBannerStyleFactory {
    /** Creates an Int UI light information [InlineBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Information.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark information [InlineBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Information.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}

/** Provides access to the [IntUiInlineSuccessBannerStyleFactory] via the [InlineBannerStyle] companion. */
public val InlineBannerStyle.Companion.Success: IntUiInlineSuccessBannerStyleFactory
    get() = IntUiInlineSuccessBannerStyleFactory

/** Factory object for creating Int UI success-variant [InlineBannerStyle] instances. */
public object IntUiInlineSuccessBannerStyleFactory {
    /** Creates an Int UI light success [InlineBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Success.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark success [InlineBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Success.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}

/** Provides access to the [IntUiSuccessBannerColorFactory] via the [BannerColors] companion. */
public val BannerColors.Companion.Success: IntUiSuccessBannerColorFactory
    get() = IntUiSuccessBannerColorFactory

/** Provides access to the [IntUiInlineWarningBannerStyleFactory] via the [InlineBannerStyle] companion. */
public val InlineBannerStyle.Companion.Warning: IntUiInlineWarningBannerStyleFactory
    get() = IntUiInlineWarningBannerStyleFactory

/** Factory object for creating Int UI warning-variant [InlineBannerStyle] instances. */
public object IntUiInlineWarningBannerStyleFactory {
    /** Creates an Int UI light warning [InlineBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Warning.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark warning [InlineBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Warning.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}

/** Provides access to the [IntUiInlineErrorBannerStyleFactory] via the [InlineBannerStyle] companion. */
public val InlineBannerStyle.Companion.Error: IntUiInlineErrorBannerStyleFactory
    get() = IntUiInlineErrorBannerStyleFactory

/** Factory object for creating Int UI error-variant [InlineBannerStyle] instances. */
public object IntUiInlineErrorBannerStyleFactory {
    /** Creates an Int UI light error [InlineBannerStyle] with the provided parameters. */
    public fun light(
        colors: BannerColors = BannerColors.Error.light(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)

    /** Creates an Int UI dark error [InlineBannerStyle] with the provided parameters. */
    public fun dark(
        colors: BannerColors = BannerColors.Error.dark(),
        metrics: BannerMetrics = BannerMetrics.default(),
    ): InlineBannerStyle = InlineBannerStyle(colors = colors, metrics = metrics)
}
// endregion
