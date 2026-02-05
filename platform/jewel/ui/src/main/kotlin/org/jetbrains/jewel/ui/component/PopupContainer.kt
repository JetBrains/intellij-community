package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
 * When a maxHeight is specified, the container becomes scrollable and displays a vertical scrollbar, ensuring both the
 * main content and ad content can be scrolled together.
 *
 * This variant includes the `useIntrinsicWidth` parameter for controlling width measurement behavior, along with key
 * event handling.
 *
 * **Usage example:**
 *
 * ```kotlin
 * PopupContainer(
 *     onDismissRequest = { /* handle dismiss */ },
 *     horizontalAlignment = Alignment.Start,
 *     useIntrinsicWidth = true,
 *     maxHeight = 400.dp,
 *     onKeyEvent = { event -> /* handle key */ },
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
 * @param useIntrinsicWidth Whether to use IntrinsicSize.Max for width measurement. Set to true when you want the popup
 *   to size to its widest content. Set to false when content contains SubcomposeLayout-based components (LazyColumn,
 *   LazyRow, etc.) which don't support intrinsic measurements
 * @param maxHeight The maximum height of the popup. When specified, enables scrolling for overflow content
 * @param style The visual styling configuration for the popup container
 * @param popupProperties Properties controlling the popup window behavior
 * @param popupPositionProvider Determines the position of the popup on the screen
 * @param onPreviewKeyEvent Optional callback for preview key events, returns true if the event was handled
 * @param onKeyEvent Optional callback for key events, returns true if the event was handled
 * @param adContent Optional composable content to display at the bottom of the popup, typically used for hints or
 *   promotional messages
 * @param content The main content to display inside the popup container
 */
@Composable
public fun PopupContainer(
    onDismissRequest: () -> Unit,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    useIntrinsicWidth: Boolean = false,
    maxHeight: Dp = Dp.Unspecified,
    style: PopupContainerStyle = JewelTheme.popupContainerStyle,
    popupProperties: PopupProperties = PopupProperties(focusable = true),
    popupPositionProvider: PopupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = style.metrics.offset,
            contentMargin = style.metrics.menuMargin,
            alignment = horizontalAlignment,
            density = LocalDensity.current,
        ),
    onPreviewKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    adContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    PopupContainerImpl(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = horizontalAlignment,
        useIntrinsicWidth = useIntrinsicWidth,
        modifier = modifier,
        maxHeight = maxHeight,
        style = style,
        popupProperties = popupProperties,
        popupPositionProvider = popupPositionProvider,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        adContent = adContent,
        content = content,
    )
}

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
@Deprecated(
    message =
        "Deprecated in favor of the method with 'useIntrinsicWidth', 'maxHeight', 'onPreviewKeyEvent' and 'onKeyEvent' parameters",
    level = DeprecationLevel.HIDDEN,
)
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
    PopupContainerImpl(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = horizontalAlignment,
        useIntrinsicWidth = false,
        modifier = modifier,
        maxHeight = Dp.Unspecified,
        style = style,
        popupProperties = popupProperties,
        popupPositionProvider = popupPositionProvider,
        onPreviewKeyEvent = null,
        onKeyEvent = null,
        adContent = adContent,
        content = content,
    )
}

@Composable
@Deprecated(
    message =
        "Deprecated in favor of the method with 'adContent', 'useIntrinsicWidth', 'maxHeight', 'onPreviewKeyEvent' and 'onKeyEvent' parameters",
    level = DeprecationLevel.HIDDEN,
)
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
    PopupContainerImpl(
        onDismissRequest = onDismissRequest,
        horizontalAlignment = horizontalAlignment,
        useIntrinsicWidth = false,
        modifier = modifier,
        maxHeight = Dp.Unspecified,
        style = style,
        popupProperties = popupProperties,
        popupPositionProvider = popupPositionProvider,
        onPreviewKeyEvent = null,
        onKeyEvent = null,
        adContent = null,
        content = content,
    )
}

@Composable
private fun PopupContainerImpl(
    onDismissRequest: () -> Unit,
    horizontalAlignment: Alignment.Horizontal,
    useIntrinsicWidth: Boolean,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Unspecified,
    style: PopupContainerStyle = JewelTheme.popupContainerStyle,
    popupProperties: PopupProperties = PopupProperties(focusable = true),
    popupPositionProvider: PopupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = style.metrics.offset,
            contentMargin = style.metrics.menuMargin,
            alignment = horizontalAlignment,
            density = LocalDensity.current,
        ),
    onPreviewKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    adContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = popupProperties,
        onPreviewKeyEvent = onPreviewKeyEvent ?: { false },
        onKeyEvent = onKeyEvent ?: { false },
        cornerSize = style.metrics.cornerSize,
    ) {
        OverrideDarkMode(style.isDark) {
            val colors = style.colors
            val popupShape = RoundedCornerShape(style.metrics.cornerSize)

            val baseModifier =
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

            val widthModifier = if (useIntrinsicWidth) Modifier.width(IntrinsicSize.Max) else Modifier

            // Wrap content slots to preserve state when reused in branches
            val movableContent = remember { movableContentOf { content() } }
            val movableAdContent =
                adContent?.let { ad ->
                    remember { movableContentOf { PopupAd(modifier = Modifier.fillMaxWidth()) { ad() } } }
                }

            if (maxHeight != Dp.Unspecified) {
                // Scrollable mode: content + adContent scroll together
                val scrollState = rememberScrollState()
                Box(modifier = baseModifier.then(widthModifier).heightIn(max = maxHeight)) {
                    Column(modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()) {
                        movableContent()
                        movableAdContent?.invoke()
                    }

                    VerticalScrollbar(
                        rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                    )
                }
            } else {
                // Non-scrollable mode: original behavior
                Column(modifier = baseModifier.then(widthModifier)) {
                    movableContent()
                    movableAdContent?.invoke()
                }
            }
        }
    }
}
