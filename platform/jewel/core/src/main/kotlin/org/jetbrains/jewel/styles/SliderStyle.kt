package org.jetbrains.jewel.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.IntelliJTypography
import org.jetbrains.jewel.Orientation

typealias SliderStyle = ControlStyle<SliderAppearance, SliderState>

data class SliderAppearance(
    val labelTextStyle: TextStyle,
    val background: Color,
    val orientation: Orientation,
    val paintTrack: Boolean,
    val paintTicks: Boolean,
    val inverted: Boolean,
    val majorTickSpacing: Int,
    val minorTickSpacing: Int
)

// TODO this is horrible, we should get rid of it (but this is Swing interop so...)
object SliderState

fun SliderStyle.appearance() = appearance(SliderState)

val LocalSliderStyle = compositionLocalOf<SliderStyle> { localNotProvided() }

val Styles.slider: SliderStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalSliderStyle.current

fun SliderStyle(
    palette: IntelliJPalette,
    typography: IntelliJTypography,
    orientation: Orientation = Orientation.Horizontal,
    paintTrack: Boolean = true,
    paintTicks: Boolean = false,
    inverted: Boolean = false,
    majorTickSpacing: Int = 10,
    minorTickSpacing: Int = 1
) = SliderStyle {
    // TODO create variants (with/without ticks?) maybe
    default {
        state(
            SliderState,
            SliderAppearance(
                labelTextStyle = typography.slider.copy(color = palette.slider.foreground),
                background = palette.slider.background,
                orientation = orientation,
                paintTrack = paintTrack,
                paintTicks = paintTicks,
                inverted = inverted,
                majorTickSpacing = majorTickSpacing,
                minorTickSpacing = minorTickSpacing
            )
        )
    }
}
