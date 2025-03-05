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
import org.jetbrains.jewel.ui.icon.IconKey

@Immutable
@GenerateDataFunctions
public class RadioButtonStyle(
    public val colors: RadioButtonColors,
    public val metrics: RadioButtonMetrics,
    public val icons: RadioButtonIcons,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RadioButtonStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        return result
    }

    override fun toString(): String = "RadioButtonStyle(colors=$colors, metrics=$metrics, icons=$icons)"

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
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RadioButtonColors

        if (content != other.content) return false
        if (contentHovered != other.contentHovered) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentSelected != other.contentSelected) return false
        if (contentSelectedHovered != other.contentSelectedHovered) return false
        if (contentSelectedDisabled != other.contentSelectedDisabled) return false

        return true
    }

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + contentHovered.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentSelected.hashCode()
        result = 31 * result + contentSelectedHovered.hashCode()
        result = 31 * result + contentSelectedDisabled.hashCode()
        return result
    }

    override fun toString(): String {
        return "RadioButtonColors(" +
            "content=$content, " +
            "contentHovered=$contentHovered, " +
            "contentDisabled=$contentDisabled, " +
            "contentSelected=$contentSelected, " +
            "contentSelectedHovered=$contentSelectedHovered, " +
            "contentSelectedDisabled=$contentSelectedDisabled" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class RadioButtonMetrics(
    public val radioButtonSize: DpSize,
    public val outlineSize: DpSize,
    public val outlineFocusedSize: DpSize,
    public val outlineSelectedSize: DpSize,
    public val outlineSelectedFocusedSize: DpSize,
    public val iconContentGap: Dp,
) {
    @Composable
    public fun outlineSizeFor(state: RadioButtonState): State<DpSize> =
        rememberUpdatedState(
            when {
                state.isFocused && state.isSelected -> outlineSelectedFocusedSize
                !state.isFocused && state.isSelected -> outlineSelectedSize
                state.isFocused && !state.isSelected -> outlineFocusedSize
                else -> outlineSize
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RadioButtonMetrics

        if (radioButtonSize != other.radioButtonSize) return false
        if (outlineSize != other.outlineSize) return false
        if (outlineFocusedSize != other.outlineFocusedSize) return false
        if (outlineSelectedSize != other.outlineSelectedSize) return false
        if (outlineSelectedFocusedSize != other.outlineSelectedFocusedSize) return false
        if (iconContentGap != other.iconContentGap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = radioButtonSize.hashCode()
        result = 31 * result + outlineSize.hashCode()
        result = 31 * result + outlineFocusedSize.hashCode()
        result = 31 * result + outlineSelectedSize.hashCode()
        result = 31 * result + outlineSelectedFocusedSize.hashCode()
        result = 31 * result + iconContentGap.hashCode()
        return result
    }

    override fun toString(): String {
        return "RadioButtonMetrics(" +
            "radioButtonSize=$radioButtonSize, " +
            "outlineSize=$outlineSize, " +
            "outlineFocusedSize=$outlineFocusedSize, " +
            "outlineSelectedSize=$outlineSelectedSize, " +
            "outlineSelectedFocusedSize=$outlineSelectedFocusedSize, " +
            "iconContentGap=$iconContentGap" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class RadioButtonIcons(public val radioButton: IconKey) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RadioButtonIcons

        return radioButton == other.radioButton
    }

    override fun hashCode(): Int = radioButton.hashCode()

    override fun toString(): String = "RadioButtonIcons(radioButton=$radioButton)"

    public companion object
}

public val LocalRadioButtonStyle: ProvidableCompositionLocal<RadioButtonStyle> = staticCompositionLocalOf {
    error("No RadioButtonStyle provided. Have you forgotten the theme?")
}
