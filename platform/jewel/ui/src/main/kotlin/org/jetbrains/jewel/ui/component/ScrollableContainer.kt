@file:Suppress("DuplicatedCode") // Lots of identical-looking but not deduplicable code

package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.WhenScrolling
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

private const val ID_CONTENT = "VerticallyScrollableContainer_content"
private const val ID_VERTICAL_SCROLLBAR = "VerticallyScrollableContainer_verticalScrollbar"
private const val ID_HORIZONTAL_SCROLLBAR = "VerticallyScrollableContainer_horizontalScrollbar"

@Composable
public fun VerticallyScrollableContainer(
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
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
        horizontalScrollbar = null,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT).verticalScroll(scrollState)) { content() }
    }
}

@Composable
internal fun TextAreaScrollableContainer(
    scrollState: ScrollState,
    style: ScrollbarStyle,
    contentModifier: Modifier,
    content: @Composable () -> Unit,
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
        horizontalScrollbar = null,
        modifier = Modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(contentModifier.layoutId(ID_CONTENT)) { content() }
    }
}

@Composable
public fun VerticallyScrollableContainer(
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
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
        horizontalScrollbar = null,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

@Composable
public fun VerticallyScrollableContainer(
    scrollState: LazyGridState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
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
        horizontalScrollbar = null,
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

@Composable
public fun HorizontallyScrollableContainer(
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = null,
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
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT).horizontalScroll(scrollState)) { content() }
    }
}

@Composable
public fun HorizontallyScrollableContainer(
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = null,
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
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

@Composable
public fun HorizontallyScrollableContainer(
    scrollState: LazyGridState,
    modifier: Modifier = Modifier,
    scrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    scrollbarEnabled: Boolean = true,
    scrollbarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = null,
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
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

@ScheduledForRemoval(inVersion = "Before Jewel 1.0")
@Deprecated("Not properly supported due to Compose limitations", level = DeprecationLevel.HIDDEN)
@ExperimentalJewelApi
@Composable
public fun ScrollableContainer(
    modifier: Modifier = Modifier,
    verticalScrollState: ScrollState = rememberScrollState(),
    horizontalScrollState: ScrollState = rememberScrollState(),
    verticalScrollbarModifier: Modifier = Modifier,
    horizontalScrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    content: @Composable () -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = {
            VerticalScrollbar(verticalScrollState, verticalScrollbarModifier, style = style, keepVisible = keepVisible)
        },
        horizontalScrollbar = {
            HorizontalScrollbar(
                horizontalScrollState,
                horizontalScrollbarModifier,
                style = style,
                keepVisible = keepVisible,
            )
        },
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT).verticalScroll(verticalScrollState).horizontalScroll(horizontalScrollState)) {
            content()
        }
    }
}

@ScheduledForRemoval(inVersion = "Before Jewel 1.0")
@Deprecated("Not properly supported due to Compose limitations", level = DeprecationLevel.HIDDEN)
@ExperimentalJewelApi
@Composable
public fun ScrollableContainer(
    verticalScrollState: LazyListState,
    horizontalScrollState: LazyListState,
    modifier: Modifier = Modifier,
    verticalScrollbarModifier: Modifier = Modifier,
    horizontalScrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    content: @Composable () -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = {
            VerticalScrollbar(verticalScrollState, verticalScrollbarModifier, style = style, keepVisible = keepVisible)
        },
        horizontalScrollbar = {
            HorizontalScrollbar(
                horizontalScrollState,
                horizontalScrollbarModifier,
                style = style,
                keepVisible = keepVisible,
            )
        },
        modifier = modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) { keepVisible = it },
        scrollbarStyle = style,
    ) {
        Box(Modifier.layoutId(ID_CONTENT)) { content() }
    }
}

@ScheduledForRemoval(inVersion = "Before Jewel 1.0")
@Deprecated("Not properly supported due to Compose limitations", level = DeprecationLevel.HIDDEN)
@ExperimentalJewelApi
@Composable
public fun ScrollableContainer(
    verticalScrollState: LazyGridState,
    horizontalScrollState: LazyGridState,
    modifier: Modifier = Modifier,
    verticalScrollbarModifier: Modifier = Modifier,
    horizontalScrollbarModifier: Modifier = Modifier,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    content: @Composable () -> Unit,
) {
    var keepVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScrollableContainerImpl(
        verticalScrollbar = {
            VerticalScrollbar(verticalScrollState, verticalScrollbarModifier, style = style, keepVisible = keepVisible)
        },
        horizontalScrollbar = {
            HorizontalScrollbar(
                horizontalScrollState,
                horizontalScrollbarModifier,
                style = style,
                keepVisible = keepVisible,
            )
        },
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
    horizontalScrollbar: (@Composable () -> Unit)?,
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
        val sizeOffsetWhenBothVisible =
            if (verticalScrollbarMeasurable != null && horizontalScrollbarMeasurable != null) {
                scrollbarStyle.scrollbarVisibility.trackThicknessExpanded.roundToPx()
            } else 0

        val verticalScrollbarPlaceable =
            if (verticalScrollbarMeasurable != null) {
                val verticalScrollbarConstraints =
                    Constraints.fixedHeight(incomingConstraints.maxHeight - sizeOffsetWhenBothVisible)
                verticalScrollbarMeasurable.measure(verticalScrollbarConstraints)
            } else null

        val horizontalScrollbarPlaceable =
            if (horizontalScrollbarMeasurable != null) {
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

    fun width() =
        if (incomingConstraints.hasBoundedWidth) {
            val maxWidth = incomingConstraints.maxWidth
            when {
                !isMacOs -> maxWidth // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> maxWidth - (verticalScrollbarPlaceable?.width ?: 0)
                visibility is WhenScrolling -> maxWidth
                else -> error("Unsupported visibility style: $visibility")
            }
        } else {
            error("Incoming constraints have infinite width, should not use fixed width")
        }

    fun height() =
        if (incomingConstraints.hasBoundedHeight) {
            val maxHeight = incomingConstraints.maxHeight
            when {
                !isMacOs -> maxHeight // Scrollbars on Win/Linux are always overlaid
                visibility is AlwaysVisible -> maxHeight - (horizontalScrollbarPlaceable?.height ?: 0)
                visibility is WhenScrolling -> maxHeight
                else -> error("Unsupported visibility style: $visibility")
            }
        } else {
            error("Incoming constraints have infinite height, should not use fixed height")
        }

    return when {
        incomingConstraints.hasBoundedWidth && incomingConstraints.hasBoundedHeight -> {
            Constraints.fixed(width(), height())
        }
        !incomingConstraints.hasBoundedWidth && incomingConstraints.hasBoundedHeight -> {
            Constraints.fixedHeight(height())
        }
        incomingConstraints.hasBoundedWidth && !incomingConstraints.hasBoundedHeight -> {
            Constraints.fixedWidth(width())
        }
        else -> Constraints()
    }
}

/**
 * A content padding to apply when you want to ensure the content is not overlapped by scrollbars. This value can be
 * used for both vertical and horizontal scrollbars.
 *
 * When the [style] is [AlwaysVisible], this value is zero, since the various `ScrollableContainer`s will prevent
 * overlapping anyway. If it is [WhenScrolling], this value will be the maximum thickness of the scrollbar.
 */
@Composable
public fun scrollbarContentSafePadding(style: ScrollbarStyle = JewelTheme.scrollbarStyle): Dp =
    when {
        hostOs != OS.MacOS -> style.scrollbarVisibility.trackThicknessExpanded
        style.scrollbarVisibility is AlwaysVisible -> 0.dp
        style.scrollbarVisibility is WhenScrolling -> style.scrollbarVisibility.trackThicknessExpanded
        else -> error("Unsupported visibility: ${style.scrollbarVisibility}")
    }
