// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownText
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultErrorBanner
import org.jetbrains.jewel.ui.component.DefaultInformationBanner
import org.jetbrains.jewel.ui.component.DefaultSuccessBanner
import org.jetbrains.jewel.ui.component.DefaultWarningBanner
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
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

@Composable
public fun Banners(modifier: Modifier = Modifier) {
    Column(modifier) {
        var clickLabel by remember { mutableStateOf("") }
        Text(text = "Clicked action: $clickLabel")
        Spacer(Modifier.height(8.dp))

        VerticallyScrollableContainer {
            Column(
                Modifier.fillMaxWidth().padding(end = scrollbarContentSafePadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupHeader("Default banner (aka editor banners)")

                DefaultInformationBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    linkActions = {
                        action("Action A", onClick = { clickLabel = "Info default with icon Action A clicked" })
                        action("Action B", onClick = { clickLabel = "Info default with icon Action B clicked" })
                    },
                )

                DefaultInformationBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            null,
                            onClick = { clickLabel = "Banner 'close' icon action clicked" },
                        )
                    },
                    content = {
                        MarkdownText(
                            "This is a **Markdown** banner — [watch](https://youtu.be/dQw4w9WgXcQ) `this` out ;)"
                        )
                    },
                )

                DefaultInformationBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    icon = null,
                    linkActions = {
                        action("Action A", onClick = { clickLabel = "Info default no icon Action A clicked" })
                        action("Action B", onClick = { clickLabel = "Info default no icon Action B clicked" })
                    },
                )

                DefaultInformationBanner(style = JewelTheme.defaultBannerStyle.information, text = LONG_IPSUM)

                DefaultInformationBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = LONG_IPSUM,
                    icon = null,
                )

                DefaultInformationBanner(
                    style = JewelTheme.defaultBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                )

                DefaultSuccessBanner(
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    style = JewelTheme.defaultBannerStyle.success,
                )

                DefaultWarningBanner(
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    style = JewelTheme.defaultBannerStyle.warning,
                )

                DefaultErrorBanner(
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    modifier = Modifier,
                    icon = { Icon(AllIconsKeys.General.BalloonError, null) },
                    linkActions = {
                        action("Action A", onClick = { clickLabel = "Error default Action A clicked" })
                        action("Action B", onClick = { clickLabel = "Error default Action B clicked" })
                        action("Action C", onClick = { clickLabel = "Error default Action C clicked" })
                        action("Action D", onClick = { clickLabel = "Error default Action D clicked" })
                    },
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Gear,
                            "Settings",
                            "Open settings",
                            onClick = { clickLabel = "Gear Click" },
                        )
                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            null,
                            onClick = { clickLabel = "Info inline no icon Action Icon clicked" },
                        )
                    },
                    style = JewelTheme.defaultBannerStyle.error,
                )

                Spacer(Modifier.height(8.dp))

                GroupHeader("Inline banner")

                var showTitle by remember { mutableStateOf(false) }
                var optionalTitle: String? by remember { mutableStateOf(null) }
                CheckboxRow(
                    checked = showTitle,
                    onCheckedChange = {
                        showTitle = it
                        optionalTitle = if (showTitle) "I'm an optional title " + LONG_IPSUM else null
                    },
                ) {
                    Text("Show optional title")
                }

                InlineInformationBanner(
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    title = optionalTitle,
                    icon = null,
                    style = JewelTheme.inlineBannerStyle.information,
                )

                InlineInformationBanner(
                    title = optionalTitle,
                    icon = null,
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            onClick = { clickLabel = "Info inline no icon Action Icon clicked" },
                        )
                    },
                    style = JewelTheme.inlineBannerStyle.information,
                    content = {
                        MarkdownText(
                            "This is a **Markdown** banner with a custom font — [watch](https://youtu.be/dQw4w9WgXcQ) `this` out ;)",
                            fontFamily = FontFamily.Cursive,
                            fontSize = 22.sp,
                        )
                    },
                )

                var restart by remember { mutableIntStateOf(0) }
                var timer by remember { mutableDoubleStateOf(0.0) }
                LaunchedEffect(restart) {
                    val initialTime = withFrameMillis { it }
                    while (true) {
                        withFrameMillis { timer = (it - initialTime) / 1000.0 }
                    }
                }

                InlineInformationBanner(
                    title = optionalTitle,
                    icon = null,
                    iconActions = { iconAction(AllIconsKeys.General.Refresh, "Restart", onClick = { restart += 1 }) },
                    style = JewelTheme.inlineBannerStyle.information,
                    content = { MarkdownText("Timer — **${"%.2f".format(timer)}** _seconds remaining_.") },
                )

                InlineInformationBanner(
                    icon = null,
                    style = JewelTheme.inlineBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    title = optionalTitle,
                    linkActions = {
                        action("Action A", onClick = { clickLabel = "Info inline no icon Action A clicked" })
                        action("Action B", onClick = { clickLabel = "Info inline no icon Action B clicked" })
                    },
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Gear,
                            "Settings",
                            "Open settings",
                            onClick = { clickLabel = "Gear Click" },
                        )

                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            null,
                            onClick = { clickLabel = "Info inline no icon Action Icon clicked" },
                        )
                    },
                )

                InlineInformationBanner(
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    title = optionalTitle,
                    icon = null,
                    linkActions = {
                        action("Action A", onClick = { clickLabel = "Info inline no icon Action A clicked" })
                        action("Action B", onClick = { clickLabel = "Info inline no icon Action B clicked" })
                    },
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            onClick = { clickLabel = "Info inline no icon Action Icon clicked" },
                        )
                    },
                    style = JewelTheme.inlineBannerStyle.information,
                )

                InlineInformationBanner(
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    title = optionalTitle,
                    linkActions = null,
                    style = JewelTheme.inlineBannerStyle.information,
                )
                InlineErrorBanner(
                    style = JewelTheme.inlineBannerStyle.error,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    title = optionalTitle,
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            onClick = { clickLabel = "Error Inline Action Icon clicked" },
                        )
                    },
                )
                InlineInformationBanner(
                    style = JewelTheme.inlineBannerStyle.information,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    title = optionalTitle,
                    linkActions = {
                        action("Action A", onClick = { clickLabel = "Information Inline Action A clicked" })
                        action("Action B", onClick = { clickLabel = "Information Inline Action B clicked" })
                    },
                )
                InlineSuccessBanner(
                    style = JewelTheme.inlineBannerStyle.success,
                    text = LONG_IPSUM,
                    title = optionalTitle,
                    linkActions = {
                        action(
                            "Action A with a text that is so long that make other actions hide in the menu",
                            onClick = { clickLabel = "Success Inline Action A clicked" },
                        )
                        action(
                            "Action B with a longer text to check how overflow behaves",
                            onClick = { clickLabel = "Success Inline Action B clicked" },
                        )
                        action(
                            "Action C that also has a very long text to make it overflow to the menu",
                            onClick = { clickLabel = "Success Inline Action B clicked" },
                        )
                    },
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Gear,
                            "Settings",
                            onClick = { clickLabel = "Success Gear Icon clicked" },
                        )
                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            onClick = { clickLabel = "Success Close Icon clicked" },
                        )
                    },
                )
                InlineWarningBanner(
                    style = JewelTheme.inlineBannerStyle.warning,
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt",
                    title = optionalTitle,
                    linkActions = { action("Action A", onClick = { clickLabel = "Warning Inline Action A clicked" }) },
                    iconActions = {
                        iconAction(
                            AllIconsKeys.General.Gear,
                            "Settings",
                            onClick = { clickLabel = "Error Gear Icon clicked" },
                        )
                        iconAction(
                            AllIconsKeys.General.Close,
                            "Close",
                            onClick = { clickLabel = "Error Close Icon clicked" },
                        )
                    },
                )
            }
        }
    }
}
