// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ErrorDefaultBanner
import org.jetbrains.jewel.ui.component.ErrorInlineBanner
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.InformationDefaultBanner
import org.jetbrains.jewel.ui.component.InformationInlineBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.SuccessDefaultBanner
import org.jetbrains.jewel.ui.component.SuccessInlineBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.WarningDefaultBanner
import org.jetbrains.jewel.ui.component.WarningInlineBanner
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.defaultBannerStyle
import org.jetbrains.jewel.ui.theme.inlineBannerStyle

private const val LONG_IPSUM =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor" +
        " incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, " +
        "quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
        "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu " +
        "fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa " +
        "qui officia deserunt mollit anim id est laborum."

@ExperimentalLayoutApi
@Composable
public fun Banners() {
    Column {
        var clickLabel by remember { mutableStateOf("") }
        Text(text = "Clicked action: $clickLabel")
        Spacer(Modifier.height(8.dp))

        VerticallyScrollableContainer {
            Column(
                Modifier.fillMaxWidth().padding(end = scrollbarContentSafePadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupHeader("Default banner (aka editor banners)")

                InformationDefaultBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    actions = {
                        Link("Action A", onClick = { clickLabel = "Info default no icon Action A clicked" })
                        Link("Action B", onClick = { clickLabel = "Info default no icon Action B clicked" })
                    },
                )

                InformationDefaultBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    icon = null,
                    actions = {
                        Link("Action A", onClick = { clickLabel = "Info default no icon Action A clicked" })
                        Link("Action B", onClick = { clickLabel = "Info default no icon Action B clicked" })
                    },
                )

                InformationDefaultBanner(style = JewelTheme.defaultBannerStyle.information, text = LONG_IPSUM)

                InformationDefaultBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = LONG_IPSUM,
                    icon = null,
                )

                InformationDefaultBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                )

                SuccessDefaultBanner(
                    style = JewelTheme.defaultBannerStyle.success,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                )

                WarningDefaultBanner(
                    style = JewelTheme.defaultBannerStyle.warning,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                )

                ErrorDefaultBanner(
                    style = JewelTheme.defaultBannerStyle.error,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                )

                Spacer(Modifier.height(8.dp))

                GroupHeader("Inline banner")

                InformationInlineBanner(
                    icon = null,
                    style = JewelTheme.inlineBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                )

                InformationInlineBanner(
                    icon = null,
                    style = JewelTheme.inlineBannerStyle.information,
                    text = LONG_IPSUM,
                    actionIcons = {
                        IconButton(onClick = { clickLabel = "Info inline no icon Action Icon clicked" }) {
                            Icon(AllIconsKeys.General.Close, null)
                        }
                    },
                )

                InformationInlineBanner(
                    icon = null,
                    style = JewelTheme.inlineBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    actions = {
                        Link("Action A", onClick = { clickLabel = "Info inline no icon Action A clicked" })
                        Link("Action B", onClick = { clickLabel = "Info inline no icon Action B clicked" })
                    },
                )

                InformationInlineBanner(
                    icon = null,
                    style = JewelTheme.inlineBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    actionIcons = {
                        IconButton(onClick = { clickLabel = "Info inline no icon Action Icon clicked" }) {
                            Icon(AllIconsKeys.General.Close, null)
                        }
                    },
                    actions = {
                        Link("Action A", onClick = { clickLabel = "Info inline no icon Action A clicked" })
                        Link("Action B", onClick = { clickLabel = "Info inline no icon Action B clicked" })
                    },
                )

                InformationInlineBanner(
                    style = JewelTheme.inlineBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                )
                ErrorInlineBanner(
                    style = JewelTheme.inlineBannerStyle.error,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    actionIcons = {
                        IconButton(onClick = { clickLabel = "Error Inline Action Icon clicked" }) {
                            Icon(AllIconsKeys.General.Close, null)
                        }
                    },
                )
                InformationInlineBanner(
                    style = JewelTheme.inlineBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    actions = {
                        Link("Action A", onClick = { clickLabel = "Information Inline Action A clicked" })
                        Link("Action B", onClick = { clickLabel = "Information Inline Action B clicked" })
                    },
                )
                SuccessInlineBanner(
                    style = JewelTheme.inlineBannerStyle.success,
                    text = LONG_IPSUM,
                    actions = {
                        Link("Action A", onClick = { clickLabel = "Success Inline Action A clicked" })
                        Link("Action B", onClick = { clickLabel = "Success Inline Action B clicked" })
                    },
                    actionIcons = {
                        IconButton(onClick = { clickLabel = "Error Close Icon clicked" }) {
                            Icon(AllIconsKeys.General.Close, null)
                        }
                        IconButton(onClick = { clickLabel = "Error Gear Icon clicked" }) {
                            Icon(AllIconsKeys.General.Gear, null)
                        }
                    },
                )
                WarningInlineBanner(
                    style = JewelTheme.inlineBannerStyle.warning,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    actions = { Link("Action A", onClick = { clickLabel = "Warning Inline Action A clicked" }) },
                    actionIcons = {
                        IconButton(onClick = { clickLabel = "Error Close Icon clicked" }) {
                            Icon(AllIconsKeys.General.Close, null)
                        }
                        IconButton(onClick = { clickLabel = "Error Gear Icon clicked" }) {
                            Icon(AllIconsKeys.General.Gear, null)
                        }
                    },
                )
            }
        }
    }
}
