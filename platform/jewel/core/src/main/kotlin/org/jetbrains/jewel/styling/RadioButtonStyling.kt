package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.RadioButtonState

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
        }
    )

    val buttonColors: RadioButtonButtonColors
}

// TODO these should be used to tint the SVGs
@Immutable
interface RadioButtonButtonColors {
    val fill: Color
    val fillHovered: Color
    val fillDisabled: Color
    val fillSelected: Color
    val fillSelectedHovered: Color
    val fillSelectedDisabled: Color

    @Composable
    fun fillFor(state: RadioButtonState) = rememberUpdatedState(
        when {
            !state.isEnabled && state.isSelected -> fillSelectedDisabled
            !state.isEnabled -> fillDisabled
            state.isSelected && state.isHovered -> fillSelectedHovered
            state.isSelected -> fillSelected
            state.isHovered -> fillHovered
            else -> fill
        }
    )

    val border: Color
    val borderHovered: Color
    val borderDisabled: Color
    val borderSelected: Color
    val borderSelectedHovered: Color
    val borderSelectedDisabled: Color

    @Composable
    fun borderFor(state: RadioButtonState) = rememberUpdatedState(
        when {
            !state.isEnabled && state.isSelected -> borderSelectedDisabled
            !state.isEnabled -> borderDisabled
            state.isSelected && state.isHovered -> borderSelectedHovered
            state.isSelected -> borderSelected
            state.isHovered -> borderHovered
            else -> border
        }
    )

    val markSelected: Color
    val markSelectedHovered: Color
    val markSelectedDisabled: Color

    @Composable
    fun markFor(state: RadioButtonState) = rememberUpdatedState(
        when {
            !state.isSelected -> Color.Unspecified
            !state.isEnabled -> markSelectedDisabled
            state.isHovered -> markSelectedHovered
            else -> markSelected
        }
    )
}

@Immutable
interface RadioButtonMetrics {

    val radioButtonSize: DpSize
    val iconContentGap: Dp
}

@Immutable
interface RadioButtonIcons : StatefulPainterProvider<RadioButtonState> {

    val unselected: OutlineResourcePainterProvider<RadioButtonState>
    val selected: OutlineResourcePainterProvider<RadioButtonState>

    @Composable
    override fun getPainter(
        state: RadioButtonState,
        resourceLoader: ResourceLoader
    ): State<Painter> {
        val iconProvider = if (state.isSelected) selected else unselected
        return iconProvider.getPainter(state, resourceLoader)
    }
}

val LocalRadioButtonStyle = staticCompositionLocalOf<RadioButtonStyle> {
    error("No RadioButtonStyle provided")
}
