package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

typealias PainterProvider = @Composable () -> Painter

class IntelliJPainters(
    val checkbox: CheckboxPainters,
    val radioButton: RadioButtonPainters,
    val treeView: TreeViewPainters
) {

    data class TreeViewPainters(
        val arrow: PainterProvider
    ) {

        companion object
    }

    data class CheckboxPainters(
        val unselected: PainterProvider,
        val unselectedDisabled: PainterProvider,
        val unselectedFocused: PainterProvider,
        val selected: PainterProvider,
        val selectedDisabled: PainterProvider,
        val selectedFocused: PainterProvider,
        val indeterminate: PainterProvider,
        val indeterminateDisabled: PainterProvider,
        val indeterminateFocused: PainterProvider
    ) {

        companion object
    }

    data class RadioButtonPainters(
        val unselected: PainterProvider,
        val unselectedDisabled: PainterProvider,
        val unselectedFocused: PainterProvider,
        val selected: PainterProvider,
        val selectedDisabled: PainterProvider,
        val selectedFocused: PainterProvider
    ) {

        companion object
    }

    companion object
}
