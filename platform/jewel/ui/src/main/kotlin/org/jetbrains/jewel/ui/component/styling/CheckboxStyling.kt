package org.jetbrains.jewel.ui.component.styling

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
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.CheckboxState
import org.jetbrains.jewel.ui.painter.PainterProvider

@Immutable
@GenerateDataFunctions
class CheckboxStyle(
    val colors: CheckboxColors,
    val metrics: CheckboxMetrics,
    val icons: CheckboxIcons,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class CheckboxColors(
    val content: Color,
    val contentDisabled: Color,
    val contentSelected: Color,
) {

    @Composable
    fun contentFor(state: CheckboxState) = rememberUpdatedState(
        when {
            !state.isEnabled -> contentDisabled
            state.toggleableState == ToggleableState.On -> contentSelected
            else -> content
        },
    )

    companion object
}

@Immutable
@GenerateDataFunctions
class CheckboxMetrics(
    val checkboxSize: DpSize,
    val checkboxCornerSize: CornerSize,
    val outlineSize: DpSize,
    val outlineOffset: DpOffset,
    val iconContentGap: Dp,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class CheckboxIcons(val checkbox: PainterProvider) {

    companion object
}

val LocalCheckboxStyle = staticCompositionLocalOf<CheckboxStyle> {
    error("No CheckboxStyle provided")
}
