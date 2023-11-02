package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.RadioButtonState
import org.jetbrains.jewel.ui.painter.PainterProvider

@Immutable
@GenerateDataFunctions
public class RadioButtonStyle(
    public val colors: RadioButtonColors,
    public val metrics: RadioButtonMetrics,
    public val icons: RadioButtonIcons,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class RadioButtonColors(
    public val content: Color,
    public val contentHovered: Color,
    public val contentDisabled: Color,
    public val contentSelected: Color,
    public val contentSelectedHovered: Color,
    public val contentSelectedDisabled: Color,
) {

    @Composable
    public fun contentFor(state: RadioButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled && state.isSelected -> contentSelectedDisabled
                !state.isEnabled -> contentDisabled
                state.isSelected && state.isHovered -> contentSelectedHovered
                state.isSelected -> contentSelected
                state.isHovered -> contentHovered
                else -> content
            },
        )

    public companion object
}

@Immutable
@GenerateDataFunctions
public class RadioButtonMetrics(
    public val radioButtonSize: DpSize,
    public val iconContentGap: Dp,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class RadioButtonIcons(public val radioButton: PainterProvider) {

    public companion object
}

public val LocalRadioButtonStyle: ProvidableCompositionLocal<RadioButtonStyle> =
    staticCompositionLocalOf {
        error("No RadioButtonStyle provided. Have you forgotten the theme?")
    }
