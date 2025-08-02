@file:Suppress("DuplicatedCode") // Lots of identical-looking but not deduplicable code

package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.WhenScrolling
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

private const val ID_CONTENT = "VerticallyScrollableContainer_content"
private const val ID_VERTICAL_SCROLLBAR = "VerticallyScrollableContainer_verticalScrollbar"
private const val ID_HORIZONTAL_SCROLLBAR = "VerticallyScrollableContainer_horizontalScrollbar"

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapts to the platform conventions.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/scrollbar.html)
 *
 * **Usage example:**
 * [`Scrollbars.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt)
 *
 * **Swing equivalent:**
 * [`JBScrollBar`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBScrollBar.java)
 *
 * @param modifier Modifier to be applied to the container
 * @param scrollbarModifier Modifier to be applied to the scrollbar
 * @param scrollState The state of the scroll
 * @param style The visual styling for the scrollbar
 * @param reverseLayout Reverse the direction of scrolling, when `true`, 0 [ScrollState.value] will mean bottom, when
 *   `false`, 0 [ScrollState.value] will mean top
 * @param scrollbarEnabled Whether scrolling is enabled or not
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun VerticallyScrollableContainer(
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = {
            VerticalScrollbar(
                scrollState = scrollState,
                modifier = scrollbarModifier,
                style = style,
                keepVisible = keepVisible,
                enabled = scrollbarEnabled,
                reverseLayout = reverseLayout,
                interactionSource = scrollbarInteractionSource,
            )
        },
        verticalScrollbarVisible = scrollState.canScroll,
        horizontalScrollbar = null,
        horizontalScrollbarVisible = false,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT).verticalScroll(scrollState, reverseScrolling = reverseLayout)) { content() }
    }
}

@Composable
internal fun TextAreaScrollableContainer(
    scrollState: ScrollState,
    style: ScrollbarStyle,
    contentModifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = {
            VerticalScrollbar(
                scrollState,
                style = style,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Default).padding(1.dp),
                keepVisible = keepVisible,
            )
        },
        verticalScrollbarVisible = scrollState.canScroll,
        horizontalScrollbar = null,
        horizontalScrollbarVisible = false,
        modifier = Modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(contentModifier.layoutId(ID_CONTENT)) { content() }
    }
}

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapts to the platform conventions.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/scrollbar.html)
 *
 * **Usage example:**
 * [`Scrollbars.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt)
 *
 * **Swing equivalent:**
 * [`JBScrollBar`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBScrollBar.java)
 *
 * @param scrollState The state of the scroll
 * @param modifier Modifier to be applied to the container
 * @param scrollbarModifier Modifier to be applied to the scrollbar
 * @param style The visual styling for the scrollbar
 * @param reverseLayout Reverse the direction of scrolling, when `true`, 0 [ScrollState.value] will mean bottom, when
 *   `false`, 0 [ScrollState.value] will mean top
 * @param scrollbarEnabled Whether scrolling is enabled or not
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun VerticallyScrollableContainer(
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = {
            VerticalScrollbar(
                scrollState = scrollState,
                modifier = scrollbarModifier,
                style = style,
                keepVisible = keepVisible,
                enabled = scrollbarEnabled,
                reverseLayout = reverseLayout,
                interactionSource = scrollbarInteractionSource,
            )
        },
        verticalScrollbarVisible = scrollState.canScroll,
        horizontalScrollbar = null,
        horizontalScrollbarVisible = false,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapts to the platform conventions.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/scrollbar.html)
 *
 * **Usage example:**
 * [`Scrollbars.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt)
 *
 * **Swing equivalent:**
 * [`JBScrollBar`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBScrollBar.java)
 *
 * @param scrollState The state of the scroll
 * @param modifier Modifier to be applied to the container
 * @param scrollbarModifier Modifier to be applied to the scrollbar
 * @param style The visual styling for the scrollbar
 * @param reverseLayout Reverse the direction of scrolling, when `true`, 0 [ScrollState.value] will mean bottom, when
 *   `false`, 0 [ScrollState.value] will mean top
 * @param scrollbarEnabled Whether scrolling is enabled or not
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun VerticallyScrollableContainer(
    scrollState: LazyGridState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = {
            VerticalScrollbar(
                scrollState = scrollState,
                modifier = scrollbarModifier,
                style = style,
                keepVisible = keepVisible,
                enabled = scrollbarEnabled,
                reverseLayout = reverseLayout,
                interactionSource = scrollbarInteractionSource,
            )
        },
        verticalScrollbarVisible = scrollState.canScroll,
        horizontalScrollbar = null,
        horizontalScrollbarVisible = false,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapts to the platform conventions.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/scrollbar.html)
 *
 * **Usage example:**
 * [`Scrollbars.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt)
 *
 * **Swing equivalent:**
 * [`JBScrollBar`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBScrollBar.java)
 *
 * @param modifier Modifier to be applied to the container
 * @param scrollbarModifier Modifier to be applied to the scrollbar
 * @param scrollState The state object to control and observe scrolling
 * @param style The visual styling configuration for the scrollbar
 * @param reverseLayout Whether the scrollbar should be displayed on the opposite side
 * @param scrollbarEnabled Controls whether the scrollbar is enabled
 * @param scrollbarInteractionSource Source of interactions for the scrollbar
 * @param content The content to be displayed in the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun HorizontallyScrollableContainer(
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = null,
        verticalScrollbarVisible = false,
        horizontalScrollbar = {
            HorizontalScrollbar(
                scrollState = scrollState,
                modifier = scrollbarModifier,
                style = style,
                keepVisible = keepVisible,
                enabled = scrollbarEnabled,
                reverseLayout = reverseLayout,
                interactionSource = scrollbarInteractionSource,
            )
        },
        horizontalScrollbarVisible = scrollState.canScroll,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT).horizontalScroll(scrollState, reverseScrolling = reverseLayout)) { content() }
    }
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapts to the platform conventions.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/scrollbar.html)
 *
 * **Usage example:**
 * [`Scrollbars.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt)
 *
 * **Swing equivalent:**
 * [`JBScrollBar`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBScrollBar.java)
 *
 * @param scrollState The state of the scroll
 * @param modifier Modifier to be applied to the container
 * @param scrollbarModifier Modifier to be applied to the scrollbar
 * @param style The visual styling for the scrollbar
 * @param reverseLayout Reverse the direction of scrolling, when `true`, 0 [ScrollState.value] will mean bottom, when
 *   `false`, 0 [ScrollState.value] will mean top
 * @param scrollbarEnabled Whether scrolling is enabled or not
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun HorizontallyScrollableContainer(
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = null,
        verticalScrollbarVisible = false,
        horizontalScrollbar = {
            HorizontalScrollbar(
                scrollState = scrollState,
                modifier = scrollbarModifier,
                style = style,
                keepVisible = keepVisible,
                enabled = scrollbarEnabled,
                reverseLayout = reverseLayout,
                interactionSource = scrollbarInteractionSource,
            )
        },
        horizontalScrollbarVisible = scrollState.canScroll,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapts to the platform conventions.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/scrollbar.html)
 *
 * **Usage example:**
 * [`Scrollbars.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt)
 *
 * **Swing equivalent:**
 * [`JBScrollBar`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/components/JBScrollBar.java)
 *
 * @param scrollState The state of the scroll
 * @param modifier Modifier to be applied to the container
 * @param scrollbarModifier Modifier to be applied to the scrollbar
 * @param style The visual styling for the scrollbar
 * @param reverseLayout Reverse the direction of scrolling, when `true`, 0 [ScrollState.value] will mean bottom, when
 *   `false`, 0 [ScrollState.value] will mean top
 * @param scrollbarEnabled Whether scrolling is enabled or not
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun HorizontallyScrollableContainer(
    scrollState: LazyGridState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = null,
        verticalScrollbarVisible = false,
        horizontalScrollbar = {
            HorizontalScrollbar(
                scrollState = scrollState,
                modifier = scrollbarModifier,
                style = style,
                keepVisible = keepVisible,
                enabled = scrollbarEnabled,
                reverseLayout = reverseLayout,
                interactionSource = scrollbarInteractionSource,
            )
        },
        horizontalScrollbarVisible = scrollState.canScroll,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

private fun Modifier.withKeepVisible(
    lingerDuration: Duration,
    scope: CoroutineScope,
    onKeepVisibleChange: (Boolean) -> Unit,
) =
    pointerInput(scope) {
        var delayJob: Job? = null
        awaitEachGesture {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Move) {
                delayJob?.cancel()
                onKeepVisibleChange(true)

                @Suppress("AssignedValueIsNeverRead") // It's read on each gesture, two lines above; false positive
                delayJob =
                    scope.launch {
                        delay(lingerDuration)
                        onKeepVisibleChange(false)
                    }
            }
        }
    }

@Composable
private fun ScrollableContainerImpl(
    verticalScrollbar: (@Composable () -> Unit)?,
    verticalScrollbarVisible: Boolean,
    horizontalScrollbar: (@Composable () -> Unit)?,
    horizontalScrollbarVisible: Boolean,
    modifier: Modifier,
    scrollbarStyle: ScrollbarStyle,
    content: @Composable () -> Unit,
) {
    Layout(
        content = {
            content()

            if (verticalScrollbar != null) {
                Box(Modifier.layoutId(ID_VERTICAL_SCROLLBAR)) { verticalScrollbar() }
            }

            if (horizontalScrollbar != null) {
                Box(Modifier.layoutId(ID_HORIZONTAL_SCROLLBAR)) { horizontalScrollbar() }
            }
        },
        modifier,
    ) { measurables, incomingConstraints ->
        val verticalScrollbarMeasurable = measurables.find { it.layoutId == ID_VERTICAL_SCROLLBAR }
        val horizontalScrollbarMeasurable = measurables.find { it.layoutId == ID_HORIZONTAL_SCROLLBAR }

        // Leaving the bottom-end corner empty when both scrollbars visible at the same time
        val accountForVerticalScrollbar = verticalScrollbarMeasurable != null && verticalScrollbarVisible
        val accountForHorizontalScrollbar = horizontalScrollbarMeasurable != null && horizontalScrollbarVisible
        val sizeOffsetWhenBothVisible =
            if (accountForVerticalScrollbar && accountForHorizontalScrollbar) {
                scrollbarStyle.scrollbarVisibility.trackThicknessExpanded.roundToPx()
            } else 0

        val verticalScrollbarPlaceable =
            if (accountForVerticalScrollbar) {
                val verticalScrollbarConstraints =
                    Constraints.fixedHeight(incomingConstraints.maxHeight - sizeOffsetWhenBothVisible)
                verticalScrollbarMeasurable.measure(verticalScrollbarConstraints)
            } else null

        val horizontalScrollbarPlaceable =
            if (accountForHorizontalScrollbar) {
                val horizontalScrollbarConstraints =
                    Constraints.fixedWidth(incomingConstraints.maxWidth - sizeOffsetWhenBothVisible)
                horizontalScrollbarMeasurable.measure(horizontalScrollbarConstraints)
            } else null

        val isMacOs = hostOs == OS.MacOS
        val contentMeasurable = measurables.find { it.layoutId == ID_CONTENT } ?: error("Content not provided")
        val contentConstraints =
            computeContentConstraints(
                scrollbarStyle,
                isMacOs,
                incomingConstraints,
                verticalScrollbarPlaceable,
                horizontalScrollbarPlaceable,
            )
        val contentPlaceable = contentMeasurable.measure(contentConstraints)

        val isAlwaysVisible = scrollbarStyle.scrollbarVisibility is AlwaysVisible
        val vScrollbarWidth =
            when {
                !isMacOs -> 0
                isAlwaysVisible -> verticalScrollbarPlaceable?.width ?: 0
                else -> 0
            }
        val width = contentPlaceable.width + vScrollbarWidth

        val hScrollbarHeight =
            when {
                !isMacOs -> 0
                isAlwaysVisible -> horizontalScrollbarPlaceable?.height ?: 0
                else -> 0
            }
        val height = contentPlaceable.height + hScrollbarHeight

        layout(width, height) {
            contentPlaceable.placeRelative(x = 0, y = 0, zIndex = 0f)
            verticalScrollbarPlaceable?.placeRelative(x = width - verticalScrollbarPlaceable.width, y = 0, zIndex = 1f)
            horizontalScrollbarPlaceable?.placeRelative(
                x = 0,
                y = height - horizontalScrollbarPlaceable.height,
                zIndex = 1f,
            )
        }
    }
}

private fun computeContentConstraints(
    scrollbarStyle: ScrollbarStyle,
    isMacOs: Boolean,
    incomingConstraints: Constraints,
    verticalScrollbarPlaceable: Placeable?,
    horizontalScrollbarPlaceable: Placeable?,
): Constraints {
    val visibility = scrollbarStyle.scrollbarVisibility
    val scrollbarWidth = verticalScrollbarPlaceable?.width ?: 0
    val scrollbarHeight = horizontalScrollbarPlaceable?.height ?: 0

    val maxWidth = incomingConstraints.maxWidth
    val maxHeight = incomingConstraints.maxHeight
    val minWidth = incomingConstraints.minWidth
    val minHeight = incomingConstraints.minHeight

    fun maxWidth() =
        if (incomingConstraints.hasBoundedWidth) {
            when {
                !isMacOs -> maxWidth // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> maxWidth - scrollbarWidth
                visibility is WhenScrolling -> maxWidth
                else -> error("Unsupported visibility style: $visibility")
            }
        } else {
            error("Incoming constraints have infinite width, should not use fixed width")
        }

    fun minWidth() =
        if (minWidth > 0) {
            when {
                !isMacOs -> minWidth // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> minWidth - scrollbarWidth
                visibility is WhenScrolling -> minWidth
                else -> error("Unsupported visibility style: $visibility")
            }
        } else 0

    fun maxHeight() =
        if (incomingConstraints.hasBoundedHeight) {
            when {
                !isMacOs -> maxHeight // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> maxHeight - scrollbarHeight
                visibility is WhenScrolling -> maxHeight
                else -> error("Unsupported visibility style: $visibility")
            }
        } else {
            error("Incoming constraints have infinite height, should not use fixed height")
        }

    fun minHeight() =
        if (minHeight > 0) {
            when {
                !isMacOs -> minHeight // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> minHeight - scrollbarHeight
                visibility is WhenScrolling -> minHeight
                else -> error("Unsupported visibility style: $visibility")
            }
        } else 0

    return when {
        incomingConstraints.hasBoundedWidth && incomingConstraints.hasBoundedHeight -> {
            Constraints(minWidth = minWidth(), maxWidth = maxWidth(), minHeight = minHeight(), maxHeight = maxHeight())
        }
        !incomingConstraints.hasBoundedWidth && incomingConstraints.hasBoundedHeight -> {
            incomingConstraints.copy(minHeight = minHeight(), maxHeight = maxHeight())
        }
        incomingConstraints.hasBoundedWidth && !incomingConstraints.hasBoundedHeight -> {
            incomingConstraints.copy(minWidth = minWidth(), maxWidth = maxWidth())
        }
        else -> incomingConstraints
    }
}

/**
 * Calculates the safe padding needed to prevent scrollable containers' content from being overlapped by scrollbars.
 *
 * This value can be used for both vertical and horizontal scrollbars. You can use it on the root content of a
 * scrollable container, but if you have background elements that should extend behind the scrollbars (e.g., a list
 * item's selected background), you should consider applying it instead to the actual content: text, images, etc.
 *
 * If you want to overlay something on top of a scrollable container, and avoid overlapping the scrollbars, you should
 * use
 * [`JewelTheme.scrollbarStyle.scrollbarVisibility.trackThicknessExpanded`][ScrollbarVisibility.trackThicknessExpanded].
 *
 * Returns a padding value that ensures content remains fully visible when scrollbars are present. The value depends on
 * the platform (macOS vs Windows/Linux) and the scrollbar visibility style:
 * - For macOS with always-visible scrollbars: returns 0 as the layout already accounts for the space
 * - For macOS with auto-hiding scrollbars: returns the maximum scrollbar thickness
 * - For Windows/Linux: returns the maximum scrollbar thickness plus 1.dp, as scrollbars overlay content
 *
 * @param style The scrollbar styling configuration to use for calculations. When the [style] is [AlwaysVisible], this
 *   value is zero, since the various `ScrollableContainer`s will prevent overlapping anyway. If it is [WhenScrolling],
 *   this value will be the maximum thickness of the scrollbar.
 * @return The padding needed to prevent content overlap with scrollbars
 */
@Composable
public fun scrollbarContentSafePadding(style: ScrollbarStyle = JewelTheme.scrollbarStyle): Dp =
    when {
        hostOs != OS.MacOS -> style.scrollbarVisibility.trackThicknessExpanded + 1.dp
        style.scrollbarVisibility is AlwaysVisible -> 0.dp
        style.scrollbarVisibility is WhenScrolling -> style.scrollbarVisibility.trackThicknessExpanded
        else -> error("Unsupported visibility: ${style.scrollbarVisibility}")
    }

private val ScrollableState.canScroll
    get() = canScrollBackward || canScrollForward
