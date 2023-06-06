package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.tree.DefaultSelectableLazyColumnKeyActions
import org.jetbrains.jewel.foundation.tree.DefaultSelectableLazyColumnPointerEventAction
import org.jetbrains.jewel.foundation.tree.KeyBindingScopedActions
import org.jetbrains.jewel.foundation.tree.PointerEventScopedActions
import java.util.UUID

/**
 * A composable that displays a scrollable and selectable list of items in a column arrangement.
 *
 * @param modifier The modifier to apply to this layout.
 * @param state The state object that holds the state information for the selectable lazy column.
 * @param contentPadding The padding to be applied to the content of the column.
 * @param reverseLayout Whether the items should be laid out in reverse order.
 * @param verticalArrangement The vertical arrangement strategy for laying out the items.
 * @param horizontalAlignment The horizontal alignment strategy for laying out the items.
 * @param flingBehavior The fling behavior for scrolling.
 * @param interactionSource The interaction source for handling user input events.
 * @param keyActions The key binding actions for handling keyboard events.
 * @param pointerHandlingScopedActions The pointer event actions for handling pointer events.
 * @param content The content of the selectable lazy column, specified as a lambda function
 *     with a [SelectableLazyListScope] receiver.
 */
@Composable
fun SelectableLazyColumn(
    modifier: Modifier = Modifier,
    state: SelectableLazyListState = rememberSelectableLazyListState(selectionMode = SelectionMode.Multiple),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    keyActions: KeyBindingScopedActions = DefaultSelectableLazyColumnKeyActions(state),
    pointerHandlingScopedActions: PointerEventScopedActions = DefaultSelectableLazyColumnPointerEventAction(state),
    content: SelectableLazyListScope.() -> Unit
) {
    DisposableEffect(keyActions) {
        state.attachKeybindings(keyActions)
        onDispose { }
    }
    BaseSelectableLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        interactionSource = interactionSource,
        keyActions = keyActions.handleOnKeyEvent(rememberCoroutineScope()),
        pointerHandlingScopedActions = pointerHandlingScopedActions,
        content = content
    )
}

@Composable
internal fun BaseSelectableLazyColumn(
    modifier: Modifier = Modifier,
    state: SelectableLazyListState = rememberSelectableLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    keyActions: KeyEvent.(Int) -> Boolean = { false },
    pointerHandlingScopedActions: PointerEventScopedActions,
    content: SelectableLazyListScope.() -> Unit
) {
    val uuid = remember { UUID.randomUUID().toString() }
    state.checkUUID(uuid)
    LazyColumn(
        modifier = modifier
            .onPreviewKeyEvent { event -> state.lastFocusedIndex?.let { keyActions.invoke(event, it) } ?: false }
            .focusable(interactionSource = interactionSource),
        state = state.lazyListState,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior
    ) {
        state.clearKeys()
        SelectableLazyListScopeContainer(state, pointerHandlingScopedActions).apply(content)
    }
}

/**
 * Creates a container for a selectable lazy list scope within a lazy list scope.
 *
 * @param state The state object for the selectable lazy list.
 * @param pointerHandlingScopedActions The pointer event scoped actions for handling pointer events.
 *
 * @return A [SelectableLazyListScopeContainer] object that encapsulates the selectable lazy list scope.
 */
internal fun LazyListScope.SelectableLazyListScopeContainer(state: SelectableLazyListState, pointerHandlingScopedActions: PointerEventScopedActions) =
    SelectableLazyListScopeContainer(this, state, pointerHandlingScopedActions)
