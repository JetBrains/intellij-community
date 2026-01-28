package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
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
                metrics = defaultBannerMetrics(),
            ),
        success =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = defaultBannerMetrics(),
            ),
        warning =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = defaultBannerMetrics(),
            ),
        error =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.ERROR_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = defaultBannerMetrics(),
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
                metrics = defaultBannerMetrics(),
            ),
        success =
            InlineBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = defaultBannerMetrics(),
            ),
        warning =
            InlineBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = defaultBannerMetrics(),
            ),
        error =
            InlineBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.ERROR_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = defaultBannerMetrics(),
            ),
    )

private fun defaultBannerMetrics() =
    BannerMetrics(
        borderWidth,
        CornerSize(8.dp), // Swing uses arc diameter of 16, which is radius of 8
        PaddingValues(vertical = 12.dp, horizontal = 12.dp), // Swing uses JBUI.Borders.empty(12)
    )
