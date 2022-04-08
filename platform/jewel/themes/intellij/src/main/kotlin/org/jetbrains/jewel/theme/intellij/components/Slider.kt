package org.jetbrains.jewel.theme.intellij.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.Orientation
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants

// TODO pull out state (min, max, value)
@Composable
fun Slider(
    min: Int,
    max: Int,
    value: Int,
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Vertical,
) {
    SwingPanel(
        background = Color.White,
        modifier = modifier,
        factory = {
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(jSlider(min, max, value, orientation))
            }
        }
    )
}

private fun jSlider(
    min: Int,
    max: Int,
    value: Int,
    orientation: Orientation = Orientation.Vertical
) = JSlider(orientation.toSwingInt(), min, max, value).apply {

}

private fun Orientation.toSwingInt() = when (this) {
    Orientation.Horizontal -> SwingConstants.HORIZONTAL
    Orientation.Vertical -> SwingConstants.VERTICAL
}

