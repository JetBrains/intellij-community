package org.jetbrains.jewel.theme.intellij

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Dp

class IntelliJMetrics(
    val gridSize: Dp,
    val singlePadding: Dp,
    val doublePadding: Dp,
    val controlFocusHaloWidth: Dp, // Component.focusWidth
    val controlFocusHaloArc: Dp, // Component.focusWidth
    val controlArc: Dp, // Component.arc
    val button: Button,
    val separator: Separator,
    val scrollbar: Scrollbar,
) {

    data class Button(
        val strokeWidth: Dp, // N/A in Swing
        val arc: CornerSize, // Button.arc
        val padding: PaddingValues // Button.margin
    ) {

        companion object
    }

    data class Separator(
        val strokeWidth: Dp, // N/A in Swing
    ) {

        companion object
    }

    data class Scrollbar(
        val minSize: Dp, // ScrollBar.minimumThumbSize
        val thickness: Dp, // N/A in Swing
        val thumbCornerSize: CornerSize, // See com.intellij.ui.components.ScrollBarPainter.Thumb.paint
    ) {

        companion object
    }

    companion object
}
