package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.SegmentedControlState

public class SegmentedControlStyle(
    public val colors: SegmentedControlColors,
    public val metrics: SegmentedControlMetrics,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SegmentedControlColors(
    public val border: Brush,
    public val borderDisabled: Brush,
    public val borderPressed: Brush,
    public val borderHovered: Brush,
    public val borderFocused: Brush,
) {

    @Composable
    public fun borderFor(state: SegmentedControlState): State<Brush> =
        rememberUpdatedState(
            when {
                state.isFocused && state.isEnabled -> borderFocused
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = border,
                        disabled = borderDisabled,
                        pressed = borderPressed,
                        hovered = borderHovered,
                        active = border,
                    )
            }
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class SegmentedControlMetrics(public val cornerSize: CornerSize, public val borderWidth: Dp) {

    public companion object
}

@Composable
private fun <T> SegmentedControlState.chooseValueIgnoreCompat(
    normal: T,
    disabled: T,
    pressed: T,
    hovered: T,
    active: T,
): T =
    when {
        !isEnabled -> disabled
        isPressed -> pressed
        isHovered -> hovered
        isActive -> active
        else -> normal
    }

public val LocalSegmentedControlStyle: ProvidableCompositionLocal<SegmentedControlStyle> = staticCompositionLocalOf {
    error("No LocalSegmentedControlStyle provided. Have you forgotten the theme?")
}
