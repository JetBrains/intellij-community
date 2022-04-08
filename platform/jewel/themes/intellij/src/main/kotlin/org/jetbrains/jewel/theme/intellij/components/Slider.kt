package org.jetbrains.jewel.theme.intellij.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.theme.intellij.styles.SliderAppearance
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants

// TODO pull out state (min, max, value)
@Composable
fun Slider(
    value: Int,
    modifier: Modifier = Modifier,
    appearance: SliderAppearance = SliderAppearance(),
    onValueChange: (Int) -> Unit
) {
    SwingPanel(
        background = Color.White,
        modifier = modifier,
        factory = {
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(jSlider(appearance, value, onValueChange))
            }
        }
    )
}

private fun jSlider(
    appearance: SliderAppearance,
    value: Int,
    onValueChange: (Int) -> Unit
) = JSlider(
    appearance.orientation.toSwingInt(),
    appearance.min,
    appearance.max,
    value
).apply {
    addChangeListener { onValueChange(it.) }
}

private fun Orientation.toSwingInt() = when (this) {
    Orientation.Horizontal -> SwingConstants.HORIZONTAL
    Orientation.Vertical -> SwingConstants.VERTICAL
}

