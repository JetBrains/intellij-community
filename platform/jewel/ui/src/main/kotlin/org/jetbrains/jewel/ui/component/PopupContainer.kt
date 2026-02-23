package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.popupShadowAndBorder
import org.jetbrains.jewel.ui.theme.popupContainerStyle

/**
 * A reusable popup container that provides standard visual styling for floating content.
 *
 * Creates a popup window with configurable positioning, appearance, and optional advertising content at the bottom. The
 * container automatically applies shadows, borders, rounded corners, and background colors based on the provided style,
 * ensuring visual consistency across the application.
 *
 * **Usage example:**
 *
 * ```kotlin
 * PopupContainer(
 *     onDismissRequest = { /* handle dismiss */ },
 *     horizontalAlignment = Alignment.Start,
 *     adContent = { Text("Tip: Use Ctrl+Space for suggestions") }
 * ) {
 *     // Your popup content here
 *     Text("Popup content")
 * }
 * ```
 *
 * @param onDismissRequest Called when the popup should be dismissed (e.g., clicking outside or pressing Esc)
 * @param horizontalAlignment The horizontal alignment of the popup relative to its anchor point
 * @param modifier Modifier to be applied to the container
 * @param style The visual styling configuration for the popup container
 * @param popupProperties Properties controlling the popup window behavior
 * @param popupPositionProvider Determines the position of the popup on the screen
 * @param adContent Optional composable content to display at the bottom of the popup, typically used for hints or
 *   promotional messages
 * @param content The main content to display inside the popup container
 */
@Composable
public fun PopupContainer(
    onDismissRequest: () -> Unit,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    style: PopupContainerStyle = JewelTheme.popupContainerStyle,
    popupProperties: PopupProperties = PopupProperties(focusable = true),
    popupPositionProvider: PopupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = style.metrics.offset,
            contentMargin = style.metrics.menuMargin,
            alignment = horizontalAlignment,
            density = LocalDensity.current,
        ),
    adContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = popupProperties,
        cornerSize = style.metrics.cornerSize,
    ) {
        OverrideDarkMode(style.isDark) {
            val colors = style.colors
            val popupShape = RoundedCornerShape(style.metrics.cornerSize)

            Column(
                modifier =
                    modifier
                        .popupShadowAndBorder(
                            shape = popupShape,
                            shadowSize = style.metrics.shadowSize,
                            shadowColor = colors.shadow,
                            borderWidth = style.metrics.borderWidth,
                            borderColor = colors.border,
                        )
                        .background(colors.background, popupShape)
                        .clip(popupShape)
            ) {
                content()

                adContent?.let { PopupAd(modifier = Modifier.fillMaxWidth()) { it() } }
            }
        }
    }
}

@Composable
@Deprecated(message = "Deprecated in favor of the method with 'adContent' parameter", level = DeprecationLevel.HIDDEN)
public fun PopupContainer(
    onDismissRequest: () -> Unit,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    style: PopupContainerStyle = JewelTheme.popupContainerStyle,
    popupProperties: PopupProperties = PopupProperties(focusable = true),
    popupPositionProvider: PopupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = style.metrics.offset,
            contentMargin = style.metrics.menuMargin,
            alignment = horizontalAlignment,
            density = LocalDensity.current,
        ),
    content: @Composable () -> Unit,
) {
    PopupContainer(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = horizontalAlignment,
        modifier = modifier,
        style = style,
        popupProperties = popupProperties,
        popupPositionProvider = popupPositionProvider,
        content = content,
    )
}
