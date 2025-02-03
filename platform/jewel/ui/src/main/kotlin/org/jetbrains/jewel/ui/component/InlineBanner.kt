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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.styling.InlineBannerStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.inlineBannerStyle

/**
 * Displays an informational inline banner providing subtle, non-intrusive context or feedback within a particular UI
 * section.
 *
 * The banner uses the inline "information" theme by default, featuring an optional icon, a context message, and
 * optional actions.
 *
 * @param text the primary content of the banner, briefly describing the information it conveys.
 * @param modifier a [Modifier] to customize the layout and appearance of the banner.
 * @param icon a composable representation of an optional 16x16 dp "information" icon displayed to the left of the
 *   banner. Pass `null` to hide the icon. By default, it shows the "information" icon.
 * @param actions a lambda defining optional interactive components (e.g., links, buttons) placed inside the banner for
 *   inline actions like "View More" or "Dismiss."
 * @param actionIcons a lambda defining composable icons representing secondary or quick actions, such as closing the
 *   banner. Icons are placed on the right side of the banner.
 * @param style a [InlineBannerStyle] that determines the visual styling of the banner, using the default “information”
 *   theme. This can be overridden for customization.
 * @param textStyle typography settings applied to the banner text, based on the default Jewel theme.
 *
 * Use this banner to provide relevant, non-critical information in a compact layout.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/balloon.html)
 *
 * **Swing equivalent:**
 * [`Notifications`](https://github.com/JetBrains/intellij-community/blob/idea/243.23654.153/platform/ide-core/src/com/intellij/notification/Notifications.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * InformationInlineBanner(
 *     text = "Project indexed successfully.",
 *     actions = {
 *         Link("View Logs", onClick = { /* handle click */ })
 *     }
 * )
 * ```
 */
@Composable
public fun InformationInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonInformation, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.information,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    )
}

/**
 * Displays an inline banner to confirm successful actions or state completions in compact UI sections without
 * interrupting the workflow.
 *
 * The banner uses the inline "success" theme by default, featuring an optional icon, a success message, and optional
 * user actions.
 *
 * @param text the main content of the banner, succinctly describing the successful operation or state.
 * @param modifier a [Modifier] to customize the layout, padding, or size of the banner.
 * @param icon a composable for an optional 16x16 dp "success" icon displayed on the left side of the banner. The
 *   success icon is shown by default. Pass `null` to hide the icon.
 * @param actions a lambda defining optional interactive components, such as links or buttons, placed within the banner
 *   for additional user operations.
 * @param actionIcons a lambda defining composable icons for quick or secondary operations, such as dismissing the
 *   banner, placed on the banner's right side.
 * @param style a [InlineBannerStyle] defining the visual appearance of the banner. It uses the default "success" theme,
 *   but this can be customized if needed.
 * @param textStyle the style of the banner text, controlled by the Jewel theme's typography.
 *
 * Use this banner to provide visual feedback for completed actions.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/balloon.html)
 *
 * **Swing equivalent:**
 * [`Notifications`](https://github.com/JetBrains/intellij-community/blob/idea/243.23654.153/platform/ide-core/src/com/intellij/notification/Notifications.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * SuccessInlineBanner(
 *     text = "Build completed successfully.",
 *     actions = {
 *         Link("Details", onClick = { /* handle link */ })
 *     }
 * )
 * ```
 */
@Composable
public fun SuccessInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.Debugger.ThreadStates.Idle, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.success,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    )
}

/**
 * Shows a warning inline banner to draw attention to non-critical issues that require user awareness or resolution.
 *
 * The banner applies the inline "warning" theme by default, including an optional icon, a warning message, and
 * additional actions if provided.
 *
 * @param text the main content of the banner, describing the warning or potential issue.
 * @param modifier a [Modifier] for applying layout modifications to the banner, such as padding or size changes.
 * @param icon a composable element for an optional 16x16 dp "warning" icon displayed at the banner's left edge. The
 *   warning icon is shown by default. Set `null` to omit the icon.
 * @param actions a lambda defining interactive elements such as links or buttons, which provide additional user
 *   operations related to the warning.
 * @param actionIcons a lambda defining composable icons for secondary operations, like dismissing the banner, placed on
 *   the right side of the banner.
 * @param style an [InlineBannerStyle] defining the warning theme of the banner. The default warning theme is applied by
 *   default, but this can be customized.
 * @param textStyle typography used for the text content of the banner, inheriting from the Jewel theme.
 *
 * Use this banner for unobstructive warnings requiring user awareness.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/balloon.html)
 *
 * **Swing equivalent:**
 * [`Notifications`](https://github.com/JetBrains/intellij-community/blob/idea/243.23654.153/platform/ide-core/src/com/intellij/notification/Notifications.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * WarningInlineBanner(
 *     text = "This feature is deprecated and will be removed in the next version.",
 *     actions = {
 *         Link("Learn More", onClick = { /* handle action */ })
 *     }
 * )
 * ```
 */
@Composable
public fun WarningInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonWarning, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.warning,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    )
}

/**
 * Displays an inline banner highlighting critical errors or failures that require immediate user attention and action.
 *
 * This banner uses the inline "error" theme by default, featuring a message describing the error, and optional icons
 * and actions to assist the user in resolving it.
 *
 * @param text the main content of the banner, describing the error context or failure details.
 * @param modifier a [Modifier] for layout customization (e.g., padding, width).
 * @param icon a composable that displays an optional 16x16 dp "error" icon at the banner's left side. The error icon is
 *   displayed by default and can be omitted by passing `null`.
 * @param actions a lambda providing interactive options (e.g., retry, open settings) as part of the banner.
 * @param actionIcons a lambda defining composable icons for secondary operations, such as closing or dismissing the
 *   banner, rendered on the banner's right side.
 * @param style an [InlineBannerStyle] defining the banner's appearance under the error theme. It can be customized to
 *   override the default error styling.
 * @param textStyle typography settings for the text content, based on the default Jewel theme.
 *
 * Use this banner to convey high-priority errors affecting application behavior.
 *
 * **Guidelines:** [on IntelliJ Platform SDK webhelp](https://plugins.jetbrains.com/docs/intellij/balloon.html)
 *
 * **Swing equivalent:**
 * [`Notifications`](https://github.com/JetBrains/intellij-community/blob/idea/243.23654.153/platform/ide-core/src/com/intellij/notification/Notifications.java)
 *
 * **Usage example:**
 * [`Banners.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Banners.kt)
 *
 * ```kotlin
 * ErrorInlineBanner(
 *     text = "Connection to the server failed.",
 *     actions = {
 *         Link("Retry", onClick = { /* handle retry */ })
 *     }
 * )
 * ```
 */
@Composable
public fun ErrorInlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = { Icon(AllIconsKeys.General.BalloonError, null) },
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    actionIcons: (@Composable RowScope.() -> Unit)? = null,
    style: InlineBannerStyle = JewelTheme.inlineBannerStyle.error,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    InlineBannerImpl(
        text = text,
        style = style,
        textStyle = textStyle,
        icon = icon,
        actions = actions,
        modifier = modifier,
        actionIcons = actionIcons,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineBannerImpl(
    text: String,
    style: InlineBannerStyle,
    textStyle: TextStyle,
    icon: @Composable (() -> Unit)?,
    actions: @Composable (FlowRowScope.() -> Unit)?,
    actionIcons: @Composable (RowScope.() -> Unit)?,
    modifier: Modifier,
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
                Text(text = text, style = textStyle)

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
internal fun RoundedCornerBox(
    modifier: Modifier = Modifier,
    contentColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    cornerSize: CornerSize,
    backgroundColor: Color,
    padding: PaddingValues,
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
