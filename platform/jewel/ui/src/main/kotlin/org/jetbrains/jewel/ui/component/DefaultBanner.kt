@file:JvmName("BannerKt")

package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.banner.BannerActionsRow
import org.jetbrains.jewel.ui.component.banner.BannerIconActionScope
import org.jetbrains.jewel.ui.component.banner.BannerIconActionsRow
import org.jetbrains.jewel.ui.component.banner.BannerLinkActionScope
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.defaultBannerStyle

/**
 * Displays an informational editor banner providing a context-aware message to the user, styled with the default
 * "information" theme.
 *
 * The banner includes an optional icon, customizable actions, and a text message. Its primary purpose is to provide
 * non-intrusive, informative feedback in the editor area.
 *
 * @param text the main text content of the banner, succinctly describing the message or context.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an optional icon displayed on the left side of the banner, by default it shows
 *   the general "information" icon, in a 16x16 dp [Box]. Pass `null` to hide the icon.
 * @param actions an optional row of clickable components (e.g., links or buttons) placed at the end of the banner.
 *   Useful for actions such as "learn more," "retry," or "dismiss."
 * @param style defines the visual styling of the banner, adapting to the default "information" theme of the IJ UI. You
 *   can override it if needed by providing a custom [DefaultBannerStyle].
 * @param textStyle the styling applied to the banner's text content, using the default text style from the Jewel theme.
 *   This can be customized for specific typography needs.
 *
 * This banner is primarily used to display persistent messages without interrupting the workflow.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * InformationDefaultBanner(
 *     text = "The file has been indexed successfully.",
 *     actions = {
 *         Link("Dismiss", onClick = { /* Handle dismiss action */ })
 *     }
 * )
 * ```
 */
@Composable
@Deprecated(
    "Replace with DefaultInformationBanner",
    replaceWith =
        ReplaceWith(
            "DefaultInformationBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.DefaultInformationBanner",
        ),
)
public fun InformationDefaultBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    actions: (@Composable RowScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.information,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Displays an informational editor banner providing a context-aware message to the user, styled with the default
 * "information" theme.
 *
 * The banner includes an optional icon, customizable actions, and a text message. Its primary purpose is to provide
 * non-intrusive, informative feedback in the editor area.
 *
 * @param text the main text content of the banner, succinctly describing the message or context.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an optional icon displayed on the left side of the banner, by default it shows
 *   the general "information" icon, in a 16x16 dp [Box]. Pass `null` to hide the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style defines the visual styling of the banner, adapting to the default "information" theme of the IJ UI. You
 *   can override it if needed by providing a custom [DefaultBannerStyle].
 * @param textStyle the styling applied to the banner's text content, using the default text style from the Jewel theme.
 *   This can be customized for specific typography needs.
 *
 * This banner is primarily used to display persistent messages without interrupting the workflow.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultInformationBanner(
 *     text = "The file has been indexed successfully.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 */
@Composable
public fun DefaultInformationBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.information,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    )
}

/**
 * Displays an informational editor banner providing a context-aware message to the user, styled with the default
 * "information" theme.
 *
 * The banner includes an optional icon, customizable actions, and a text message. Its primary purpose is to provide
 * non-intrusive, informative feedback in the editor area.
 *
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an optional icon displayed on the left side of the banner, by default it shows
 *   the general "information" icon, in a 16x16 dp [Box]. Pass `null` to hide the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style defines the visual styling of the banner, adapting to the default "information" theme of the IJ UI. You
 *   can override it if needed by providing a custom [DefaultBannerStyle].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 *
 * This banner is primarily used to display persistent messages without interrupting the workflow.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultInformationBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project index **up to date**.")
 * }
 * ```
 */
@Composable
public fun DefaultInformationBanner(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.information,
    content: @Composable () -> Unit,
) {
    DefaultBannerImpl(
        style = style,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

/**
 * Displays a success editor banner signaling a successful action, state, or process completion.
 *
 * The banner includes an optional icon, customizable actions, and a text message styled with the default "success"
 * theme.
 *
 * @param text the main text content of the banner, briefly describing the successful state or process.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an optional icon displayed on the left of the banner, by default it shows the
 *   "success" icon, in a 16x16 dp [Box]. Pass `null` to hide the icon.
 * @param actions an optional row of clickable components that can act as additional user actions, such as "view
 *   details" or "close."
 * @param style defines the banner's appearance following the "success" theme of the Jewel UI, but can be overridden
 *   with custom styling.
 * @param textStyle determines the typography of the banner's text, defaulting to the application's text style.
 *
 * Use this banner to provide clear, visual feedback of a completed or successful action.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * SuccessDefaultBanner(
 *     text = "Backup completed successfully.",
 *     actions = {
 *         Link("View Logs", onClick = { /* Show logs */ })
 *     }
 * )
 * ```
 */
@Composable
@Deprecated(
    "Replace with DefaultSuccessBanner",
    replaceWith =
        ReplaceWith(
            "DefaultSuccessBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.DefaultSuccessBanner",
        ),
)
public fun SuccessDefaultBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Debugger.ThreadStates.Idle, null) },
    actions: (@Composable RowScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.success,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Displays a success editor banner signaling a successful action, state, or process completion.
 *
 * The banner includes an optional icon, customizable actions, and a text message styled with the default "success"
 * theme.
 *
 * @param text the main text content of the banner, briefly describing the successful state or process.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an optional icon displayed on the left of the banner, by default it shows the
 *   "success" icon, in a 16x16 dp [Box]. Pass `null` to hide the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style defines the banner's appearance following the "success" theme of the Jewel UI, but can be overridden
 *   with custom styling.
 * @param textStyle determines the typography of the banner's text, defaulting to the application's text style.
 *
 * Use this banner to provide clear, visual feedback of a completed or successful action.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultSuccessBanner(
 *     text = "Backup completed successfully.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 */
@Composable
public fun DefaultSuccessBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Debugger.ThreadStates.Idle, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.success,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    )
}

/**
 * Displays a success editor banner signaling a successful action, state, or process completion.
 *
 * The banner includes an optional icon, customizable actions, and a text message styled with the default "success"
 * theme.
 *
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an optional icon displayed on the left of the banner, by default it shows the
 *   "success" icon, in a 16x16 dp [Box]. Pass `null` to hide the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style defines the banner's appearance following the "success" theme of the Jewel UI, but can be overridden
 *   with custom styling.
 * @param content The primary content of the banner, briefly describing the information it conveys.
 *
 * Use this banner to provide clear, visual feedback of a completed or successful action.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultSuccessBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project indexed **successfully**.")
 * }
 * ```
 */
@Composable
public fun DefaultSuccessBanner(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Debugger.ThreadStates.Idle, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.success,
    content: @Composable () -> Unit,
) {
    DefaultBannerImpl(
        style = style,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

/**
 * Displays a warning editor banner highlighting potential issues or a need for user attention.
 *
 * This banner visually distinguishes itself using the default "warning" theme, including optional icons and user
 * actions.
 *
 * @param text the main text content of the banner, detailing the warning message or potential problem.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an icon displayed on the left of the banner, by default it shows a "warning"
 *   icon. Pass `null` to omit the icon.
 * @param actions an optional row of clickable components allowing the user to resolve the warning or access more
 *   details.
 * @param style defines the banner's appearance under the "warning" theme, or a custom [DefaultBannerStyle].
 * @param textStyle controls the typography of the warning message's text, based on the Jewel theme.
 *
 * Use this banner to make users aware of potential issues without stopping their flow.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * WarningDefaultBanner(
 *     text = "Your license is expiring soon. Please renew to avoid service interruptions.",
 *     actions = {
 *         Link("Renew Now", onClick = { /* Trigger renewal flow */ })
 *     }
 * )
 * ```
 */
@Composable
@Deprecated(
    "Replace with DefaultWarningBanner",
    replaceWith =
        ReplaceWith(
            "DefaultWarningBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.DefaultWarningBanner",
        ),
)
public fun WarningDefaultBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    actions: (@Composable RowScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.warning,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Displays a warning editor banner highlighting potential issues or a need for user attention.
 *
 * This banner visually distinguishes itself using the default "warning" theme, including optional icons and user
 * actions.
 *
 * @param text the main text content of the banner, detailing the warning message or potential problem.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an icon displayed on the left of the banner, by default it shows a "warning"
 *   icon. Pass `null` to omit the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style defines the banner's appearance under the "warning" theme, or a custom [DefaultBannerStyle].
 * @param textStyle controls the typography of the warning message's text, based on the Jewel theme.
 *
 * Use this banner to make users aware of potential issues without stopping their flow.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultWarningBanner(
 *     text = "Your license is expiring soon. Please renew to avoid service interruptions.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 */
@Composable
public fun DefaultWarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.warning,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    )
}

/**
 * Displays a warning editor banner highlighting potential issues or a need for user attention.
 *
 * This banner visually distinguishes itself using the default "warning" theme, including optional icons and user
 * actions.
 *
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an icon displayed on the left of the banner, by default it shows a "warning"
 *   icon. Pass `null` to omit the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style defines the banner's appearance under the "warning" theme, or a custom [DefaultBannerStyle].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 *
 * Use this banner to make users aware of potential issues without stopping their flow.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultWarningBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project indexed **with warnings**.")
 * }
 * ```
 */
@Composable
public fun DefaultWarningBanner(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.warning,
    content: @Composable () -> Unit,
) {
    DefaultBannerImpl(
        style = style,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

/**
 * Displays an error editor banner indicating critical issues or failures that require user awareness.
 *
 * This banner draws attention using the default "error" theme and can optionally include user actions or icons.
 *
 * @param text the main text content of the banner, focusing on the error context or failure details.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an error icon to display on the left of the banner, by default it shows the
 *   "error" icon. Pass `null` to omit the icon.
 * @param actions an optional row of interactive components for responding to the error, such as retrying or navigating
 *   to relevant settings.
 * @param style the default "error" theme styling for the banner, or a custom one via [DefaultBannerStyle].
 * @param textStyle typography settings for the text content, defaulting to Jewel's text style.
 *
 * Use this banner to provide high-visibility error messages requiring immediate user attention.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * ErrorDefaultBanner(
 *     text = "Failed to sync project files with the server.",
 *     actions = {
 *         Link("Retry", onClick = { /* Retry the action */ })
 *     }
 * )
 * ```
 */
@Composable
@Deprecated(
    "Replace with DefaultErrorBanner",
    replaceWith =
        ReplaceWith(
            "DefaultErrorBanner(" +
                "text = text, " +
                "modifier = modifier, " +
                "icon = icon, " +
                "linkActions = actions, " +
                "style = style, " +
                "textStyle = textStyle" +
                ")",
            "org.jetbrains.jewel.ui.component.DefaultErrorBanner",
        ),
)
public fun ErrorDefaultBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    actions: (@Composable RowScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.error,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Displays an error editor banner indicating critical issues or failures that require user awareness.
 *
 * This banner draws attention using the default "error" theme and can optionally include user actions or icons.
 *
 * @param text the main text content of the banner, focusing on the error context or failure details.
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an error icon to display on the left of the banner, by default it shows the
 *   "error" icon. Pass `null` to omit the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style the default "error" theme styling for the banner, or a custom one via [DefaultBannerStyle].
 * @param textStyle typography settings for the text content, defaulting to Jewel's text style.
 *
 * Use this banner to provide high-visibility error messages requiring immediate user attention.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultErrorBanner(
 *     text = "Failed to sync project files with the server.",
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * )
 * ```
 */
@Composable
public fun DefaultErrorBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.error,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
    )
}

/**
 * Displays an error editor banner indicating critical issues or failures that require user awareness.
 *
 * This banner draws attention using the default "error" theme and can optionally include user actions or icons.
 *
 * @param modifier a [Modifier] for customizing the layout and appearance of the banner.
 * @param icon a composable representing an error icon to display on the left of the banner, by default it shows the
 *   "error" icon. Pass `null` to omit the icon.
 * @param linkActions A block within the [BannerLinkActionScope] to define optional action items for the banner. If not
 *   provided, no actions will be rendered. Please note that this block will automatically fold the actions into a
 *   "More" dropdown menu if there are more than 3 actions.
 * @param iconActions A block within the [BannerIconActionScope] to define optional icon actions, such as closing the
 *   banner. Shows at the top right of the banner. If not provided, no icon actions will be rendered.
 * @param style the default "error" theme styling for the banner, or a custom one via [DefaultBannerStyle].
 * @param content The primary content of the banner, briefly describing the information it conveys.
 *
 * Use this banner to provide high-visibility error messages requiring immediate user attention.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/banner.html)
 *
 * **Swing equivalent:**
 * [`EditorNotificationProvider `](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/EditorNotificationProvider.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * Example usage:
 * ```
 * DefaultErrorBanner(
 *     actionsContent = {
 *         action("Dismiss", onClick = { /* Handle dismiss action */ })
 *         iconAction(AllIconsKeys.General.Close, "Close button", null, onClick = {  })
 *     }
 * ) {
 *     Markdown("Project index **failed**.")
 * }
 * ```
 */
@Composable
public fun DefaultErrorBanner(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    linkActions: (BannerLinkActionScope.() -> Unit)? = null,
    iconActions: (BannerIconActionScope.() -> Unit)? = null,
    style: DefaultBannerStyle = JewelTheme.defaultBannerStyle.error,
    content: @Composable () -> Unit,
) {
    DefaultBannerImpl(
        style = style,
        icon = icon,
        linkActions = linkActions,
        iconActions = iconActions,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun DefaultBannerImpl(
    text: String,
    style: DefaultBannerStyle,
    textStyle: TextStyle,
    icon: (@Composable () -> Unit)?,
    linkActions: (BannerLinkActionScope.() -> Unit)?,
    iconActions: (BannerIconActionScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    DefaultBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = buildDefaultBannerActions(linkActions, iconActions),
        modifier = modifier,
    )
}

@Composable
private fun DefaultBannerImpl(
    style: DefaultBannerStyle,
    icon: (@Composable () -> Unit)?,
    linkActions: (BannerLinkActionScope.() -> Unit)?,
    iconActions: (BannerIconActionScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DefaultBannerImpl(
        style = style,
        icon = icon,
        actions = buildDefaultBannerActions(linkActions, iconActions),
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun DefaultBannerImpl(
    text: String,
    style: DefaultBannerStyle,
    textStyle: TextStyle,
    icon: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    DefaultBannerImpl(
        style,
        icon,
        actions,
        modifier,
        { Text(text = text, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun DefaultBannerImpl(
    style: DefaultBannerStyle,
    icon: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit),
) {
    Column(modifier = modifier) {
        Divider(orientation = Orientation.Horizontal, color = style.colors.border, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.background(style.colors.background).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { icon() }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(Modifier.weight(1f)) { content() }

            if (actions != null) {
                Spacer(Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
        }
        Divider(orientation = Orientation.Horizontal, color = style.colors.border, modifier = Modifier.fillMaxWidth())
    }
}

private fun buildDefaultBannerActions(
    linkActions: (BannerLinkActionScope.() -> Unit)?,
    iconActions: (BannerIconActionScope.() -> Unit)?,
): (@Composable RowScope.() -> Unit)? =
    if (linkActions != null || iconActions != null) {
        {
            BannerActionsRow(8.dp, block = linkActions)
            BannerIconActionsRow(iconActions)
        }
    } else {
        null
    }
