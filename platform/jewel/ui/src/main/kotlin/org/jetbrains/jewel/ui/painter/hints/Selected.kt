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

/**
 * Selects the "selected" variant of an image when [selected] is true. If an image does not have a selected variant, the
 * base version will be used.
 *
 * | Base image name       | Selected image name           |
 * |-----------------------|-------------------------------|
 * | `my-icon.png`         | `my-iconSelected.png`         |
 * | `myIcon@20x20.svg`    | `myIconSelected@20x20.svg`    |
 * | `my-icon@2x_dark.png` | `my-iconSelected@2x_dark.png` |
 */
@Suppress("FunctionName")
public fun Selected(selected: Boolean = true): PainterHint = if (selected) SelectedImpl else PainterHint.None

@Suppress("FunctionName") public fun Selected(state: SelectableComponentState): PainterHint = Selected(state.isSelected)
