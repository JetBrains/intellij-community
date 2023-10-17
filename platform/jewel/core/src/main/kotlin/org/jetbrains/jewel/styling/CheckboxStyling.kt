package org.jetbrains.jewel.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.CheckboxState
import org.jetbrains.jewel.painter.PainterProvider

@Immutable
interface CheckboxStyle {

    val colors: CheckboxColors
    val metrics: CheckboxMetrics
    val icons: CheckboxIcons
}

// TODO these should be used to tint the SVGs
@Immutable
interface CheckboxColors {

    val checkboxBackground: Color
    val checkboxBackgroundDisabled: Color
    val checkboxBackgroundSelected: Color

    @Composable
    fun backgroundFor(state: CheckboxState) = rememberUpdatedState(
        when {
            !state.isEnabled -> checkboxBackgroundDisabled
            state.toggleableState == ToggleableState.On -> checkboxBackgroundSelected
            else -> checkboxBackground
        },
    )

    val content: Color
    val contentDisabled: Color
    val contentSelected: Color

    @Composable
    fun contentFor(state: CheckboxState) = rememberUpdatedState(
        when {
            !state.isEnabled -> contentDisabled
            state.toggleableState == ToggleableState.On -> contentSelected
            else -> content
        },
    )
}

@Immutable
interface CheckboxMetrics {

    val checkboxSize: DpSize
    val checkboxCornerSize: CornerSize
    val outlineSize: DpSize
    val outlineOffset: DpOffset
    val iconContentGap: Dp
}

@Immutable
interface CheckboxIcons {

    val checkbox: PainterProvider
}

val LocalCheckboxStyle = staticCompositionLocalOf<CheckboxStyle> {
    error("No CheckboxStyle provided")
}
