package org.jetbrains.jewel.theme.intellij.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.intellij.IntelliJPalette
import org.jetbrains.jewel.theme.intellij.IntelliJTypography

@Immutable
data class SliderStyle(
    val appearance: SliderAppearance = SliderAppearance(),
)

data class SliderAppearance(
    val labelTextStyle: TextStyle,
    val min: Int = 0,
    val max: Int = 100,
    val orientation: Orientation = Orientation.Horizontal,
    val paintTrack: Boolean = true,
    val paintTicks: Boolean = false,
    val paintLabels: Boolean = false,
    val inverted: Boolean = false,
    val snapToTicks: Boolean = false,
    val snapToValue: Boolean = true,
    val labels: Map<Int, String> = emptyMap()
)

val LocalSliderStyle = compositionLocalOf { SliderStyle() }
val Styles.slider: SliderStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalSliderStyle.current

fun SliderStyle(palette: IntelliJPalette, typography: IntelliJTypography): SliderStyle = SliderStyle(
    appearance = SliderAppearance(
        labelTextStyle = typography.slider.label
    )
)