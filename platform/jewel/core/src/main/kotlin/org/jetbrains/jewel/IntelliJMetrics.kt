package org.jetbrains.jewel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Dp

class IntelliJMetrics(
    val singlePadding: Dp,
    val controlFocusHaloWidth: Dp, // Component.focusWidth
    val controlFocusHaloArc: Dp, // Component.focusWidth
    val controlArc: Dp, // Component.arc
    val button: Button,
    val separator: Separator,
    val scrollbar: Scrollbar,
    val treeView: TreeView
) {

    data class TreeView(
        val indentWidth: Dp, // Tree.leftChildIndent
        val arrowEndPadding: Dp // Tree.rightChildIndent
    ) {

        companion object
    }

    data class Button(
        val strokeWidth: Dp, // N/A in Swing
        val arc: CornerSize, // Button.arc
        val padding: PaddingValues // Button.margin
    ) {

        companion object
    }

    data class Separator(
        val strokeWidth: Dp // N/A in Swing
    ) {

        companion object
    }

    data class Scrollbar(
        val minSize: Dp, // ScrollBar.minimumThumbSize
        val thickness: Dp, // N/A in Swing
        val thumbCornerSize: CornerSize // See com.intellij.ui.components.ScrollBarPainter.Thumb.paint
    ) {

        companion object
    }

    companion object
}
