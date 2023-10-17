package org.jetbrains.jewel.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.SelectableComponentState
import org.jetbrains.jewel.painter.PainterHint
import org.jetbrains.jewel.painter.PainterSuffixHint

@Immutable
private object SelectedImpl : PainterSuffixHint() {

    override fun suffix(): String = "Selected"

    override fun toString(): String = "Selected"
}

fun Selected(selected: Boolean = true): PainterHint = if (selected) {
    SelectedImpl
} else {
    PainterHint.None
}

fun Selected(state: SelectableComponentState): PainterHint = Selected(state.isSelected)
