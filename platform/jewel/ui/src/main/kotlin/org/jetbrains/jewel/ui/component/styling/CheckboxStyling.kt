package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.CheckboxState
import org.jetbrains.jewel.ui.icon.IconKey

@Immutable
@GenerateDataFunctions
public class CheckboxStyle(
    public val colors: CheckboxColors,
    public val metrics: CheckboxMetrics,
    public val icons: CheckboxIcons,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class CheckboxColors(
    public val content: Color,
    public val contentDisabled: Color,
    public val contentSelected: Color,
) {
    @Composable
    public fun contentFor(state: CheckboxState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> contentDisabled
                state.toggleableState == ToggleableState.On -> contentSelected
                else -> content
            }
        )

    public companion object
}

@Immutable
@GenerateDataFunctions
public class CheckboxMetrics(
    public val checkboxSize: DpSize,
    public val outlineCornerSize: CornerSize,
    public val outlineFocusedCornerSize: CornerSize,
    public val outlineSelectedCornerSize: CornerSize,
    public val outlineSelectedFocusedCornerSize: CornerSize,
    public val outlineSize: DpSize,
    public val outlineFocusedSize: DpSize,
    public val outlineSelectedSize: DpSize,
    public val outlineSelectedFocusedSize: DpSize,
    public val iconContentGap: Dp,
) {
    @Composable
    public fun outlineCornerSizeFor(state: CheckboxState): State<CornerSize> =
        rememberUpdatedState(
            when {
                state.isFocused && state.isSelectedOrIndeterminate -> outlineSelectedFocusedCornerSize
                !state.isFocused && state.isSelectedOrIndeterminate -> outlineSelectedCornerSize
                state.isFocused && !state.isSelectedOrIndeterminate -> outlineFocusedCornerSize
                else -> outlineCornerSize
            }
        )

    @Composable
    public fun outlineSizeFor(state: CheckboxState): State<DpSize> =
        rememberUpdatedState(
            when {
                state.isFocused && state.isSelectedOrIndeterminate -> outlineSelectedFocusedSize
                !state.isFocused && state.isSelectedOrIndeterminate -> outlineSelectedSize
                state.isFocused && !state.isSelectedOrIndeterminate -> outlineFocusedSize
                else -> outlineSize
            }
        )

    public companion object
}

@Immutable
@GenerateDataFunctions
public class CheckboxIcons(public val checkbox: IconKey) {
    public companion object
}

public val LocalCheckboxStyle: ProvidableCompositionLocal<CheckboxStyle> = staticCompositionLocalOf {
    error("No CheckboxStyle provided. Have you forgotten the theme?")
}
