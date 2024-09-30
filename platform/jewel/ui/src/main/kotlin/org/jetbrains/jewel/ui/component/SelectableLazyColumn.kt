package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.lazy.BasicSelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListScope
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.DefaultSelectableLazyColumnEventAction
import org.jetbrains.jewel.foundation.lazy.tree.DefaultSelectableLazyColumnKeyActions
import org.jetbrains.jewel.foundation.lazy.tree.KeyActions
import org.jetbrains.jewel.foundation.lazy.tree.PointerEventActions

@Composable
public fun SelectableLazyColumn(
    modifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    state: SelectableLazyListState = rememberSelectableLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    onSelectedIndexesChanged: (List<Int>) -> Unit = {},
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    keyActions: KeyActions = DefaultSelectableLazyColumnKeyActions,
    pointerEventActions: PointerEventActions = DefaultSelectableLazyColumnEventAction(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    // TODO: We will add support for styling in the near future
    content: SelectableLazyListScope.() -> Unit,
) {
    BasicSelectableLazyColumn(
        modifier = modifier,
        selectionMode = selectionMode,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        onSelectedIndexesChanged = onSelectedIndexesChanged,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        keyActions = keyActions,
        pointerEventActions = pointerEventActions,
        interactionSource = interactionSource,
        content = content,
    )
}
