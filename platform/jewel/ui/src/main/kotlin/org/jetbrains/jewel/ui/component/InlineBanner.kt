// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
@file:OptIn(ExperimentalLayoutApi::class)

package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.banner.BannerActionsRow
import org.jetbrains.jewel.ui.component.banner.BannerIconActionScope
import org.jetbrains.jewel.ui.component.banner.BannerIconActionsRow
import org.jetbrains.jewel.ui.component.banner.BannerLinkActionScope
import org.jetbrains.jewel.ui.component.styling.InlineBannerStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.inlineBannerStyle

/**
 * Displays an informational inline banner providing subtle, non-intrusive context or feedback.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InformationInlineBanner(
 *     text = "Project index up to date.",
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.General.BalloonInformation] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [text].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [text] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.information`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.information].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineInformationBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineInformationBanner",
        ),
)
public fun InformationInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.information,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    @Suppress("DEPRECATION")
    InformationInlineBanner(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Displays an informational inline banner providing subtle, non-intrusive context or feedback.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineInformationBanner(
 *     text = "Project index up to date.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.General.BalloonInformation] icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.information`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.information].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
public fun InlineInformationBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.information,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineInformationBanner(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Displays an informational inline banner providing subtle, non-intrusive context or feedback.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InformationInlineBanner(
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * ) {
 *     Markdown("Project index **up to date**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.General.BalloonInformation] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [content].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [content] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.information`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.information].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineInformationBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineInformationBanner",
        ),
)
public fun InformationInlineBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.information,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
        content = content,
    )
}

/**
 * Displays an informational inline banner providing subtle, non-intrusive context or feedback.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineInformationBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project index **up to date**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.General.BalloonInformation] icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.information`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.information].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
public fun InlineInformationBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.information,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

/**
 * Displays a success inline banner providing information about the successful completion of an operation.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * SuccessInlineBanner(
 *     text = "Project indexed successfully.",
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.Status.Success] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [text].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [text] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.success`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.success].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineSuccessBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineSuccessBanner",
        ),
)
public fun SuccessInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Status.Success, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.success,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    @Suppress("DEPRECATION")
    SuccessInlineBanner(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Displays a success inline banner providing information about the successful completion of an operation.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineSuccessBanner(
 *     text = "Project indexed successfully.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.Status.Success] icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.success`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.success].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
public fun InlineSuccessBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Status.Success, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.success,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineSuccessBanner(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Displays a success inline banner providing information about the successful completion of an operation.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * SuccessInlineBanner(
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * ) {
 *     Markdown("Project indexed **successfully**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.Status.Success] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [content].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [content] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.success`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.success].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineSuccessBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineSuccessBanner",
        ),
)
public fun SuccessInlineBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Status.Success, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.success,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
        content = content,
    )
}

/**
 * Displays a success inline banner providing information about the successful completion of an operation.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineSuccessBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project indexed **successfully**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.Status.Success] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [content].
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.success`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.success].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
public fun InlineSuccessBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Status.Success, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.success,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

/**
 * Shows a warning inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * WarningInlineBanner(
 *     text = "Project indexed with warnings.",
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.General.BalloonWarning] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [text].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [text] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.warning`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.warning].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineWarningBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineWarningBanner",
        ),
)
public fun WarningInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.warning,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    @Suppress("DEPRECATION")
    WarningInlineBanner(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Shows a warning inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineWarningBanner(
 *     text = "Project indexed with warnings.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.General.BalloonWarning] icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.warning`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.warning].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
public fun InlineWarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.warning,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineWarningBanner(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Shows a warning inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * WarningInlineBanner(
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * ) {
 *     Markdown("Project indexed **with warnings**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.General.BalloonWarning] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [content].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [content] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.warning`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.warning].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineWarningBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineWarningBanner",
        ),
)
public fun WarningInlineBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.warning,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
        content = content,
    )
}

/**
 * Shows a warning inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineWarningBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project indexed **with warnings**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.General.BalloonWarning] icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.warning`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.warning].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
public fun InlineWarningBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.warning,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        title = title,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

/**
 * Shows an error inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * ErrorInlineBanner(
 *     text = "Project indexed failed.",
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.General.BalloonError] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [text].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [text] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.error`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.error].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineErrorBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineErrorBanner",
        ),
)
public fun ErrorInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.error,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    @Suppress("DEPRECATION")
    ErrorInlineBanner(
        style = style,
        textStyle = textStyle,
        title = title,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Shows an error inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineErrorBanner(
 *     text = "Project indexed failed.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 *
 * @param text The primary content of the banner, briefly describing the information it conveys.
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [text].
 * @param icon Slot for an optional icon displayed on the left of the [text] or [title]. If null, there is no icon. By
 *   default, it is the [AllIconsKeys.General.BalloonError] icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.error`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.error].
 * @param textStyle The base [TextStyle] used by the [text] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 */
@Composable
public fun InlineErrorBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.error,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineErrorBanner(
        style = style,
        textStyle = textStyle,
        title = title,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    ) {
        Text(text = text, style = textStyle)
    }
}

/**
 * Shows an error inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * ErrorInlineBanner(
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * ) {
 *     Markdown("Project indexed **successfully**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.General.BalloonError] icon.
 * @param actions Slot for optional primary actions (usually links) to show at the bottom of the banner, below the
 *   [content].
 * @param actionIcons Slot for secondary actions (usually icon buttons), such as closing the banner to show at the top
 *   right of the banner, to the right of the [content] or [title].
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.error`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.error].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
@Deprecated(
    "Use the overload with 'linkActions' and 'iconActions' parameters instead",
    replaceWith =
        ReplaceWith(
            "InlineErrorBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "title = title, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "iconActions = actionIcons," +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.InlineErrorBanner",
        ),
)
public fun ErrorInlineBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.error,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        style = style,
        textStyle = textStyle,
        title = title,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
        content = content,
    )
}

/**
 * Shows an error inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html) (note:
 * there are no guidelines for inline banners)
 *
 * **Swing equivalent:**
 * [`InlineBanner`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/InlineBanner.kt)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InlineErrorBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project indexed **successfully**.")
 * }
 * ```
 *
 * @param modifier [Modifier] to apply to the banner.
 * @param title An optional title, rendered in bold, that appears above the [content].
 * @param icon Slot for an optional icon displayed on the left of the [content] or [title]. If null, there is no icon.
 *   By default, it is the [AllIconsKeys.General.BalloonError] icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions or there is not enough space to fit the actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style An [InlineBannerStyle] used to style the banner. The default is the theme's
 *   [`JewelTheme.inlineBannerStyle.error`][org.jetbrains.jewel.ui.component.styling.InlineBannerStyles.error].
 * @param textStyle The base [TextStyle] used by the [content] and [title]. Note that the [title] always has a
 *   [`Bold` weight][androidx.compose.ui.text.font.FontWeight.Bold].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 */
@Composable
public fun InlineErrorBanner(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.error,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable () -> Unit,
) {
    InlineBannerImpl(
        style = style,
        textStyle = textStyle,
        title = title,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineBannerImpl(
    style: InlineBannerStyle,
    textStyle: TextStyle,
    title: String?,
    icon: @Composable (() -> Unit)?,
    linkActions: (BannerLinkActionScope.() -> Unit)?,
    iconActions: (BannerIconActionScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit),
) {
    InlineBannerImpl(
        style = style,
        textStyle = textStyle,
        title = title,
        icon = icon,
        modifier = modifier,
        content = content,
        actions =
            if (linkActions != null) {
                { BannerActionsRow(16.dp, block = linkActions) }
            } else {
                null
            },
        actionIcons =
            if (iconActions != null) {
                { BannerIconActionsRow(iconActions) }
            } else {
                null
            },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineBannerImpl(
    style: InlineBannerStyle,
    textStyle: TextStyle,
    title: String?,
    icon: @Composable (() -> Unit)?,
    actions: @Composable (FlowRowScope.() -> Unit)?,
    actionIcons: @Composable (RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit),
) {
    val borderColor = style.colors.border
    RoundedCornerBox(
        modifier = modifier.testTag("InlineBanner"),
        borderColor = borderColor,
        backgroundColor = style.colors.background,
        contentColor = JewelTheme.contentColor,
        borderWidth = 1.dp,
        cornerSize = CornerSize(8.dp),
        padding = PaddingValues(),
    ) {
        Row(modifier = Modifier.padding(start = 12.dp)) {
            if (icon != null) {
                Box(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp).size(16.dp)) { icon() }
                Spacer(Modifier.width(8.dp))
            }

            Column(
                modifier =
                    Modifier.weight(1f)
                        .padding(top = 12.dp, bottom = 12.dp) // kftmt plz behave
                        .thenIf(actionIcons == null) { padding(end = 12.dp) }
            ) {
                if (title != null) {
                    Text(text = title, style = textStyle, fontWeight = Bold)
                    Spacer(Modifier.height(8.dp))
                }
                content()

                if (actions != null) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
                }
            }

            if (actionIcons != null) {
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.Top).padding(top = 8.dp, end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    actionIcons()
                }
            }
        }
    }
}

@Composable
private fun RoundedCornerBox(
    contentColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    cornerSize: CornerSize,
    backgroundColor: Color,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerSize)
    Box(
        modifier =
            modifier
                .border(borderWidth, borderColor, shape)
                .background(backgroundColor, shape)
                .clip(shape)
                .padding(padding)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}
