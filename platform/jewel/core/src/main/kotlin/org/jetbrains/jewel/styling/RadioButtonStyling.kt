package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.RadioButtonState
import org.jetbrains.jewel.painter.PainterProvider

@Immutable
interface RadioButtonStyle {

    val colors: RadioButtonColors
    val metrics: RadioButtonMetrics
    val icons: RadioButtonIcons
}

@Immutable
interface RadioButtonColors {

    val content: Color
    val contentHovered: Color
    val contentDisabled: Color
    val contentSelected: Color
    val contentSelectedHovered: Color
    val contentSelectedDisabled: Color

    @Composable
    fun contentFor(state: RadioButtonState) = rememberUpdatedState(
        when {
            !state.isEnabled && state.isSelected -> contentSelectedDisabled
            !state.isEnabled -> contentDisabled
            state.isSelected && state.isHovered -> contentSelectedHovered
            state.isSelected -> contentSelected
            state.isHovered -> contentHovered
            else -> content
        },
    )
}

@Immutable
interface RadioButtonMetrics {

    val radioButtonSize: DpSize
    val iconContentGap: Dp
}

@Immutable
interface RadioButtonIcons {

    val radioButton: PainterProvider
}

val LocalRadioButtonStyle = staticCompositionLocalOf<RadioButtonStyle> {
    error("No RadioButtonStyle provided")
}
