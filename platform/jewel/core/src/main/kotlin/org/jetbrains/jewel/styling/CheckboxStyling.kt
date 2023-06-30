package org.jetbrains.jewel.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.CheckboxState

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
        }
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
        }
    )

    val checkboxBorder: Color
    val checkboxBorderDisabled: Color
    val checkboxBorderSelected: Color

    @Composable
    fun borderFor(state: CheckboxState) = rememberUpdatedState(
        when {
            !state.isEnabled -> checkboxBorderDisabled
            state.toggleableState == ToggleableState.On -> checkboxBorderSelected
            else -> checkboxBorder
        }
    )
}

@Immutable
interface CheckboxMetrics {

    val checkboxSize: DpSize
    val checkboxCornerSize: CornerSize
    val outlineWidth: Dp
    val iconContentGap: Dp
}

@Immutable
interface CheckboxIcons : StatefulPainterProvider<CheckboxState> {

    val unchecked: StatefulPainterProvider<CheckboxState>
    val checked: StatefulPainterProvider<CheckboxState>
    val indeterminate: StatefulPainterProvider<CheckboxState>

    @Composable
    override fun getPainter(
        state: CheckboxState,
        resourceLoader: ResourceLoader
    ): State<Painter> {
        val iconProvider = when (state.toggleableState) {
            ToggleableState.On -> checked
            ToggleableState.Off -> unchecked
            ToggleableState.Indeterminate -> indeterminate
        }
        return iconProvider.getPainter(state, resourceLoader)
    }
}

val LocalCheckboxStyle = staticCompositionLocalOf<CheckboxStyle> {
    error("No CheckboxStyle provided")
}
