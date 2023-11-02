package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint

@Immutable
private object SelectedImpl : PainterSuffixHint() {

    override fun PainterProviderScope.suffix(): String = "Selected"

    override fun toString(): String = "Selected"
}

public fun Selected(selected: Boolean = true): PainterHint =
    if (selected) SelectedImpl else PainterHint.None

public fun Selected(state: SelectableComponentState): PainterHint = Selected(state.isSelected)
