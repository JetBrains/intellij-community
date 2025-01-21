package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CircleShape
import org.jetbrains.jewel.ui.component.styling.SliderColors
import org.jetbrains.jewel.ui.component.styling.SliderMetrics
import org.jetbrains.jewel.ui.component.styling.SliderStyle

internal fun readSliderStyle(dark: Boolean): SliderStyle {
    // There are no values for sliders in IntUi, so we're essentially reusing the
    // standalone colors logic, reading the palette values (and falling back to
    // hardcoded defaults).
    val colors = if (dark) SliderColors.dark() else SliderColors.light()
    return SliderStyle(colors, SliderMetrics.defaults(), CircleShape)
}
