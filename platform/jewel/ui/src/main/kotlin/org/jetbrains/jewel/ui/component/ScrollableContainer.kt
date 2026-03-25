@file:Suppress("DuplicatedCode", "KDocUnresolvedReference") // Lots of identical-looking but not deduplicable code

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
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
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

/** Defines the positioning of scrollbars within a scrollable container. */
internal enum class ScrollbarPosition {
    /**
     * Positions the scrollbar at the start of its axis.
     * - Vertical scrollbar: left edge of the container (LTR) or right edge of the container (RTL)
     * - Horizontal scrollbar: top edge of the container
     */
    Start,

    /**
     * Positions the scrollbar at the end of its axis.
     * - Vertical scrollbar: right edge of the container (LTR) or left edge of the container (RTL)
     * - Horizontal scrollbar: bottom edge of the container
     */
    End,
}

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * This version of the container owns the scrollable modifier, and you must not apply one to the [content]. If you wish
 * to retain control over the scroll modifier and own the scroll, use the overload with a [ScrollableState] parameter.
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapt to the platform conventions.
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
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not prevent the actual scrolling
 *   but only disables the scrollbars. To disable the scroll, use the overload with a `userScrollEnabled` parameter.
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
    VerticallyScrollableContainer(
        modifier,
        scrollbarModifier,
        scrollState,
        style,
        reverseLayout,
        userScrollEnabled = true,
        scrollbarEnabled,
        scrollbarInteractionSource,
        content,
    )
}

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * This version of the container owns the scrollable modifier, and you must not apply one to the [content]. If you wish
 * to retain control over the scroll modifier and own the scroll, use the overload with a [ScrollableState] parameter.
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapt to the platform conventions.
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
 * @param userScrollEnabled Whether scrolling is enabled or not
 * @param scrollbarEnabled Whether the scrollbar is enabled or not; usually matches [userScrollEnabled]
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
    userScrollEnabled: Boolean = true,
    scrollbarEnabled: Boolean = userScrollEnabled,
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
        verticalScrollbarPosition = ScrollbarPosition.End,
        horizontalScrollbar = null,
        horizontalScrollbarVisible = false,
        horizontalScrollbarPosition = ScrollbarPosition.End,
        scrollbarStyle = style,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
    ) {
        Box(
            Modifier.layoutId(ID_CONTENT)
                .verticalScroll(scrollState, reverseScrolling = reverseLayout, enabled = userScrollEnabled)
        ) {
            content()
        }
    }
}

@Suppress("ModifierNaming")
@Composable
internal fun TextAreaScrollableContainer(
    scrollState: ScrollState,
    style: ScrollbarStyle,
    contentModifier: Modifier = Modifier,
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
        verticalScrollbarPosition = ScrollbarPosition.End,
        horizontalScrollbar = null,
        horizontalScrollbarVisible = false,
        horizontalScrollbarPosition = ScrollbarPosition.End,
        scrollbarStyle = style,
        modifier = Modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
    ) {
        Box(contentModifier.layoutId(ID_CONTENT)) { content() }
    }
}

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * This version of the container does not own the scrollable modifier, but instead relies on the provided [scrollState],
 * which must be shared with the lazy layout in the [content].
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapt to the platform conventions.
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
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not impact the user's ability to
 *   scroll the container, which is controlled by the component that owns the [scrollState] (e.g., via a
 *   `userScrollEnabled` parameter)
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Deprecated(
    "Use the overload with a ScrollableState instead.",
    ReplaceWith(
        "VerticallyScrollableContainer(scrollState as ScrollableState, modifier, " +
            "scrollbarModifier, style, reverseLayout, scrollbarEnabled, scrollbarInteractionSource, content)"
    ),
    level = DeprecationLevel.HIDDEN,
)
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
    VerticallyScrollableContainer(
        scrollState as ScrollableState,
        modifier,
        scrollbarModifier,
        style,
        reverseLayout,
        scrollbarEnabled,
        scrollbarInteractionSource,
        content,
    )
}

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * This version of the container does not own the scrollable modifier, but instead relies on the provided [scrollState],
 * which must be shared with the lazy layout in the [content].
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapt to the platform conventions.
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
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not impact the user's ability to
 *   scroll the container, which is controlled by the component that owns the [scrollState] (e.g., via a
 *   `userScrollEnabled` parameter)
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Deprecated(
    "Use the overload with a ScrollableState instead.",
    ReplaceWith(
        "VerticallyScrollableContainer(scrollState as ScrollableState, modifier, " +
            "scrollbarModifier, style, reverseLayout, scrollbarEnabled, scrollbarInteractionSource, content)"
    ),
    level = DeprecationLevel.HIDDEN,
)
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
    VerticallyScrollableContainer(
        scrollState as ScrollableState,
        modifier,
        scrollbarModifier,
        style,
        reverseLayout,
        scrollbarEnabled,
        scrollbarInteractionSource,
        content,
    )
}

/**
 * A vertically scrollable container that follows the standard visual styling.
 *
 * This version of the container does not own the scrollable modifier, and as such you must apply one to the [content],
 * or use a state shared with a lazy layout in the [content]. If you wish to use a simpler version for non-lazy content
 * that owns the scroll, use the overload with an optional [ScrollState] parameter.
 *
 * Provides a container with a vertical scrollbar that matches the platform's native appearance. On macOS, the scrollbar
 * appears next to the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and behavior
 * adapt to the platform conventions.
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
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not impact the user's ability to
 *   scroll the container, which is controlled by the component that owns the [scrollState] (e.g., via a
 *   `userScrollEnabled` parameter)
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun VerticallyScrollableContainer(
    scrollState: ScrollableState,
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
        verticalScrollbarPosition = ScrollbarPosition.End,
        horizontalScrollbar = null,
        horizontalScrollbarVisible = false,
        horizontalScrollbarPosition = ScrollbarPosition.End,
        scrollbarStyle = style,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * This version of the container owns the scrollable modifier, and you must not apply one to the [content]. If you wish
 * to retain control over the scroll modifier and own the scroll, use the overload with a [ScrollableState] parameter.
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapt to the platform conventions.
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
 * @param reverseLayout Reverse the direction of scrolling, when `true`, 0 [ScrollState.value] will mean bottom, when
 *   `false`, 0 [ScrollState.value] will mean top
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not prevent the actual scrolling
 *   but only disables the scrollbars. To disable the scroll, use the overload with a `userScrollEnabled` parameter.
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
    HorizontallyScrollableContainer(
        modifier = modifier,
        scrollbarModifier = scrollbarModifier,
        scrollState = scrollState,
        style = style,
        reverseLayout = reverseLayout,
        userScrollEnabled = true,
        scrollbarEnabled = scrollbarEnabled,
        scrollbarInteractionSource = scrollbarInteractionSource,
        content = content,
    )
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * This version of the container owns the scrollable modifier, and you must not apply one to the [content]. If you wish
 * to retain control over the scroll modifier and own the scroll, use the overload with a [ScrollableState] parameter.
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapt to the platform conventions.
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
 * @param reverseLayout Reverse the direction of scrolling, when `true`, 0 [ScrollState.value] will mean bottom, when
 *   `false`, 0 [ScrollState.value] will mean top
 * @param userScrollEnabled Whether scrolling is enabled or not
 * @param scrollbarEnabled Whether scrollbars are enabled or not; usually matches [userScrollEnabled]
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
    userScrollEnabled: Boolean = true,
    scrollbarEnabled: Boolean = userScrollEnabled,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    HorizontallyScrollableContainer(
        ScrollbarPosition.End,
        modifier,
        scrollbarModifier,
        scrollState,
        style,
        reverseLayout,
        userScrollEnabled,
        scrollbarEnabled,
        scrollbarInteractionSource,
        content,
    )
}

@Composable
internal fun HorizontallyScrollableContainer(
    scrollbarPosition: ScrollbarPosition,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    userScrollEnabled: Boolean = true,
    scrollbarEnabled: Boolean = userScrollEnabled,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = null,
        verticalScrollbarVisible = false,
        verticalScrollbarPosition = ScrollbarPosition.End,
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
        horizontalScrollbarPosition = scrollbarPosition,
        scrollbarStyle = style,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
    ) {
        Box(
            Modifier.layoutId(ID_CONTENT)
                .horizontalScroll(scrollState, reverseScrolling = reverseLayout, enabled = userScrollEnabled)
        ) {
            content()
        }
    }
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * This version of the container does not own the scrollable modifier, but instead relies on the provided [scrollState],
 * which must be shared with the lazy layout in the [content].
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapt to the platform conventions.
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
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not impact the user's ability to
 *   scroll the container, which is controlled by the component that owns the [scrollState] (e.g., via a
 *   `userScrollEnabled` parameter)
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Deprecated(
    "Use the overload with a ScrollableState instead.",
    ReplaceWith(
        "HorizontallyScrollableContainer(scrollState as ScrollableState, modifier, " +
            "scrollbarModifier, style, reverseLayout, scrollbarEnabled, scrollbarInteractionSource, content)"
    ),
    level = DeprecationLevel.HIDDEN,
)
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
    HorizontallyScrollableContainer(
        scrollState as ScrollableState,
        modifier,
        scrollbarModifier,
        style,
        reverseLayout,
        scrollbarEnabled,
        scrollbarInteractionSource,
        content,
    )
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * This version of the container does not own the scrollable modifier, but instead relies on the provided [scrollState],
 * which must be shared with the lazy layout in the [content].
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapt to the platform conventions.
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
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not impact the user's ability to
 *   scroll the container, which is controlled by the component that owns the [scrollState] (e.g., via a
 *   `userScrollEnabled` parameter)
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Deprecated(
    "Use the overload with a ScrollableState instead.",
    ReplaceWith(
        "HorizontallyScrollableContainer(scrollState as ScrollableState, modifier, " +
            "scrollbarModifier, style, reverseLayout, scrollbarEnabled, scrollbarInteractionSource, content)"
    ),
    level = DeprecationLevel.HIDDEN,
)
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
    HorizontallyScrollableContainer(
        scrollState as ScrollableState,
        modifier,
        scrollbarModifier,
        style,
        reverseLayout,
        scrollbarEnabled,
        scrollbarInteractionSource,
        content,
    )
}

/**
 * A horizontally scrollable container that follows the standard visual styling.
 *
 * This version of the container does not own the scrollable modifier, and as such you must apply one to the [content],
 * or use a state shared with a lazy layout in the [content]. If you wish to use a simpler version for non-lazy content
 * that owns the scroll, use the overload with an optional [ScrollState] parameter.
 *
 * Provides a container with a horizontal scrollbar that matches the platform's native appearance. On macOS, the
 * scrollbar appears below the content, while on Windows/Linux it overlays the content. The scrollbar's visibility and
 * behavior adapt to the platform conventions.
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
 * @param scrollbarEnabled Whether the scrollbar is enabled or not. Note that this does not impact the user's ability to
 *   scroll the container, which is controlled by the component that owns the [scrollState] (e.g., via a
 *   `userScrollEnabled` parameter)
 * @param scrollbarInteractionSource The interaction source used for the scrollbar
 * @param content The main content of the scrollable container
 * @see com.intellij.ui.components.JBScrollBar
 */
@Composable
public fun HorizontallyScrollableContainer(
    scrollState: ScrollableState,
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
        verticalScrollbarPosition = ScrollbarPosition.End,
        horizontalScrollbarVisible = scrollState.canScroll,
        horizontalScrollbarPosition = ScrollbarPosition.End,
        scrollbarStyle = style,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
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
    verticalScrollbarPosition: ScrollbarPosition,
    horizontalScrollbar: (@Composable () -> Unit)?,
    horizontalScrollbarVisible: Boolean,
    horizontalScrollbarPosition: ScrollbarPosition,
    scrollbarStyle: ScrollbarStyle,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
        measurePolicy =
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints,
                ): MeasureResult {
                    if (verticalScrollbar != null) {
                        check(constraints.hasBoundedHeight) {
                            "VerticallyScrollableContainer was measured with an unbounded maximum " +
                                "height, which is not supported. One of the common reasons is " +
                                "nesting it inside another vertically scrollable layout such as " +
                                "Column(Modifier.verticalScroll()) or another " +
                                "VerticallyScrollableContainer. Use Modifier.height(), " +
                                "Modifier.fillMaxHeight(), or Modifier.weight(1f) inside a Column " +
                                "to provide a bounded height."
                        }
                    }
                    if (horizontalScrollbar != null) {
                        check(constraints.hasBoundedWidth) {
                            "HorizontallyScrollableContainer was measured with an unbounded maximum " +
                                "width, which is not supported. One of the common reasons is " +
                                "nesting it inside another horizontally scrollable layout such as " +
                                "Row(Modifier.horizontalScroll()) or another " +
                                "HorizontallyScrollableContainer. Use Modifier.width(), " +
                                "Modifier.fillMaxWidth(), or Modifier.weight(1f) inside a Row to " +
                                "provide a bounded width."
                        }
                    }

                    val verticalScrollbarMeasurable = measurables.find { it.layoutId == ID_VERTICAL_SCROLLBAR }
                    val horizontalScrollbarMeasurable = measurables.find { it.layoutId == ID_HORIZONTAL_SCROLLBAR }

                    // Leaving the bottom-end corner empty when both scrollbars visible at the same time
                    val accountForVerticalScrollbar = verticalScrollbarMeasurable != null && verticalScrollbarVisible
                    val accountForHorizontalScrollbar =
                        horizontalScrollbarMeasurable != null && horizontalScrollbarVisible
                    val sizeOffsetWhenBothVisible =
                        if (accountForVerticalScrollbar && accountForHorizontalScrollbar) {
                            scrollbarStyle.scrollbarVisibility.trackThicknessExpanded.roundToPx()
                        } else {
                            0
                        }

                    // Query scrollbar intrinsic sizes instead of measuring them upfront. This breaks
                    // the layout dependency cycle that would crash when incomingConstraints are
                    // unbounded (e.g., inside a Column with IntrinsicSize): measuring a scrollbar
                    // with fixedHeight/fixedWidth of Int.MAX_VALUE is illegal, but querying its
                    // intrinsic size is always safe.
                    val vScrollbarIntrinsicWidth =
                        if (accountForVerticalScrollbar) {
                            verticalScrollbarMeasurable.maxIntrinsicWidth(constraints.maxHeight)
                        } else {
                            0
                        }

                    val hScrollbarIntrinsicHeight =
                        if (accountForHorizontalScrollbar) {
                            horizontalScrollbarMeasurable.maxIntrinsicHeight(constraints.maxWidth)
                        } else {
                            0
                        }

                    val isMacOs = hostOs == OS.MacOS
                    val contentMeasurable =
                        measurables.find { it.layoutId == ID_CONTENT } ?: error("Content not provided")
                    val contentConstraints =
                        computeContentConstraints(
                            scrollbarStyle,
                            isMacOs,
                            constraints,
                            vScrollbarIntrinsicWidth,
                            hScrollbarIntrinsicHeight,
                        )
                    val contentPlaceable = contentMeasurable.measure(contentConstraints)

                    val isAlwaysVisible = scrollbarStyle.scrollbarVisibility is AlwaysVisible
                    val vScrollbarWidth =
                        when {
                            !isMacOs -> 0
                            isAlwaysVisible -> vScrollbarIntrinsicWidth
                            else -> 0
                        }
                    val width = contentPlaceable.width + vScrollbarWidth

                    val hScrollbarHeight =
                        when {
                            !isMacOs -> 0
                            isAlwaysVisible -> hScrollbarIntrinsicHeight
                            else -> 0
                        }
                    val height = contentPlaceable.height + hScrollbarHeight

                    // Now that content is measured and the container dimensions are known (bounded
                    // even if incomingConstraints were unbounded), measure the scrollbars with
                    // concrete sizes.
                    val verticalScrollbarPlaceable =
                        if (accountForVerticalScrollbar) {
                            val verticalScrollbarConstraints =
                                Constraints.fixedHeight(height - sizeOffsetWhenBothVisible)
                            verticalScrollbarMeasurable.measure(verticalScrollbarConstraints)
                        } else {
                            null
                        }

                    val horizontalScrollbarPlaceable =
                        if (accountForHorizontalScrollbar) {
                            val horizontalScrollbarConstraints =
                                Constraints.fixedWidth(width - sizeOffsetWhenBothVisible)
                            horizontalScrollbarMeasurable.measure(horizontalScrollbarConstraints)
                        } else {
                            null
                        }

                    return layout(width, height) {
                        contentPlaceable.placeRelative(x = 0, y = 0, zIndex = 0f)
                        verticalScrollbarPlaceable?.placeRelative(
                            x =
                                when (verticalScrollbarPosition) {
                                    ScrollbarPosition.Start -> 0
                                    ScrollbarPosition.End -> width - verticalScrollbarPlaceable.width
                                },
                            y = 0,
                            zIndex = 1f,
                        )
                        horizontalScrollbarPlaceable?.placeRelative(
                            x = 0,
                            y =
                                when (horizontalScrollbarPosition) {
                                    ScrollbarPosition.Start -> 0
                                    ScrollbarPosition.End -> height - horizontalScrollbarPlaceable.height
                                },
                            zIndex = 1f,
                        )
                    }
                }

                // Intrinsic overrides prevent the default fallback from re-running the measure
                // block with Constraints.Infinity, which would trip the bounded-constraints checks
                // above. They also provide correct values for IntrinsicSize parents.
                //
                // Cross-axis (the axis the container sizes itself to match its content):
                //   - VerticallyScrollableContainer   → width  delegates to content + scrollbar
                //   - HorizontallyScrollableContainer → height delegates to content + scrollbar
                // Scroll-axis (must be bounded by the parent — no natural size):
                //   - Always returns 0

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int,
                ): Int {
                    val contentWidth = measurables[0].maxIntrinsicWidth(height)
                    if (verticalScrollbar == null) return contentWidth
                    val isMacOs = hostOs == OS.MacOS
                    val isAlwaysVisible = scrollbarStyle.scrollbarVisibility is AlwaysVisible
                    if (!isMacOs || !isAlwaysVisible) return contentWidth
                    // Vertical scrollbar is always at index 1 when present
                    return contentWidth + measurables[1].maxIntrinsicWidth(height)
                }

                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int,
                ): Int {
                    val contentWidth = measurables[0].minIntrinsicWidth(height)
                    if (verticalScrollbar == null) return contentWidth
                    val isMacOs = hostOs == OS.MacOS
                    val isAlwaysVisible = scrollbarStyle.scrollbarVisibility is AlwaysVisible
                    if (!isMacOs || !isAlwaysVisible) return contentWidth
                    return contentWidth + measurables[1].minIntrinsicWidth(height)
                }

                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int {
                    if (verticalScrollbar != null) return 0
                    val contentHeight = measurables[0].maxIntrinsicHeight(width)
                    if (horizontalScrollbar == null) return contentHeight
                    val isMacOs = hostOs == OS.MacOS
                    val isAlwaysVisible = scrollbarStyle.scrollbarVisibility is AlwaysVisible
                    if (!isMacOs || !isAlwaysVisible) return contentHeight
                    // Horizontal scrollbar is at index 1 when only horizontal is present
                    return contentHeight + measurables[1].maxIntrinsicHeight(width)
                }

                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int {
                    if (verticalScrollbar != null) return 0
                    val contentHeight = measurables[0].minIntrinsicHeight(width)
                    if (horizontalScrollbar == null) return contentHeight
                    val isMacOs = hostOs == OS.MacOS
                    val isAlwaysVisible = scrollbarStyle.scrollbarVisibility is AlwaysVisible
                    if (!isMacOs || !isAlwaysVisible) return contentHeight
                    return contentHeight + measurables[1].minIntrinsicHeight(width)
                }
            },
    )
}

private fun computeContentConstraints(
    scrollbarStyle: ScrollbarStyle,
    isMacOs: Boolean,
    incomingConstraints: Constraints,
    verticalScrollbarWidth: Int,
    horizontalScrollbarHeight: Int,
): Constraints {
    val visibility = scrollbarStyle.scrollbarVisibility
    val scrollbarWidth = verticalScrollbarWidth
    val scrollbarHeight = horizontalScrollbarHeight

    val maxWidth = incomingConstraints.maxWidth
    val maxHeight = incomingConstraints.maxHeight
    val minWidth = incomingConstraints.minWidth
    val minHeight = incomingConstraints.minHeight

    fun maxWidth() =
        if (incomingConstraints.hasBoundedWidth) {
            when {
                !isMacOs -> maxWidth // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> adjustForScrollbar(maxWidth, scrollbarWidth)
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
                visibility is AlwaysVisible -> adjustForScrollbar(minWidth, scrollbarWidth)
                visibility is WhenScrolling -> minWidth
                else -> error("Unsupported visibility style: $visibility")
            }
        } else {
            0
        }

    fun maxHeight() =
        if (incomingConstraints.hasBoundedHeight) {
            when {
                !isMacOs -> maxHeight // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> adjustForScrollbar(maxHeight, scrollbarHeight)
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
                visibility is AlwaysVisible -> adjustForScrollbar(minHeight, scrollbarHeight)
                visibility is WhenScrolling -> minHeight
                else -> error("Unsupported visibility style: $visibility")
            }
        } else {
            0
        }

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
 * Safeguard for if the constraints provided by `Layout` are less than the dimensions of the scrollbar. This way, the
 * content will be overlaid until the next recomposition when `Layout` hands out its the proper sizing.
 */
private inline fun adjustForScrollbar(size: Int, scrollbarSize: Int) =
    if (size > scrollbarSize) {
        size - scrollbarSize
    } else {
        size
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
 * the platform (macOS vs. Windows/Linux) and the scrollbar visibility style:
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
