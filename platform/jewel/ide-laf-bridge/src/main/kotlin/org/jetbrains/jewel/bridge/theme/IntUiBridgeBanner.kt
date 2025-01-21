package org.jetbrains.jewel.bridge.theme

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.BannerColors
import org.jetbrains.jewel.ui.component.styling.BannerMetrics
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyle
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyles

internal fun readDefaultBannerStyle(): DefaultBannerStyles =
    DefaultBannerStyles(
        information =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = DarculaUIUtil.LW.dp),
            ),
        success =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = DarculaUIUtil.LW.dp),
            ),
        warning =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = DarculaUIUtil.LW.dp),
            ),
        error =
            DefaultBannerStyle(
                colors =
                    BannerColors(
                        background = JBUI.CurrentTheme.Banner.ERROR_BACKGROUND.toComposeColor(),
                        border = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR.toComposeColor(),
                    ),
                metrics = BannerMetrics(borderWidth = DarculaUIUtil.LW.dp),
            ),
    )
