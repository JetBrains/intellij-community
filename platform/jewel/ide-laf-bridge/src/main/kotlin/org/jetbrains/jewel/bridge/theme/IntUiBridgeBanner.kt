package org.jetbrains.jewel.bridge.theme

import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.BannerColors
import org.jetbrains.jewel.ui.component.styling.BannerMetrics
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyle
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyles
import org.jetbrains.jewel.ui.component.styling.InlineBannerStyle
import org.jetbrains.jewel.ui.component.styling.InlineBannerStyles

internal fun readDefaultBannerStyle(): DefaultBannerStyles =
    DefaultBannerStyles(
        information =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
        success =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
        warning =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
        error =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.ERROR_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
    )

internal fun readInlineBannerStyle(): InlineBannerStyles =
    InlineBannerStyles(
        information =
            InlineBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
        success =
            InlineBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
        warning =
            InlineBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
        error =
            InlineBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.ERROR_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = borderWidth),
            ),
    )
