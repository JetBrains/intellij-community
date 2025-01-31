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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CheckboxStyle

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

    override fun toString(): String = "CheckboxStyle(colors=$colors, metrics=$metrics, icons=$icons)"

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CheckboxColors

        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentSelected != other.contentSelected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentSelected.hashCode()
        return result
    }

    override fun toString(): String {
        return "CheckboxColors(" +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "contentSelected=$contentSelected" +
            ")"
    }

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CheckboxMetrics

        if (checkboxSize != other.checkboxSize) return false
        if (outlineCornerSize != other.outlineCornerSize) return false
        if (outlineFocusedCornerSize != other.outlineFocusedCornerSize) return false
        if (outlineSelectedCornerSize != other.outlineSelectedCornerSize) return false
        if (outlineSelectedFocusedCornerSize != other.outlineSelectedFocusedCornerSize) return false
        if (outlineSize != other.outlineSize) return false
        if (outlineFocusedSize != other.outlineFocusedSize) return false
        if (outlineSelectedSize != other.outlineSelectedSize) return false
        if (outlineSelectedFocusedSize != other.outlineSelectedFocusedSize) return false
        if (iconContentGap != other.iconContentGap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = checkboxSize.hashCode()
        result = 31 * result + outlineCornerSize.hashCode()
        result = 31 * result + outlineFocusedCornerSize.hashCode()
        result = 31 * result + outlineSelectedCornerSize.hashCode()
        result = 31 * result + outlineSelectedFocusedCornerSize.hashCode()
        result = 31 * result + outlineSize.hashCode()
        result = 31 * result + outlineFocusedSize.hashCode()
        result = 31 * result + outlineSelectedSize.hashCode()
        result = 31 * result + outlineSelectedFocusedSize.hashCode()
        result = 31 * result + iconContentGap.hashCode()
        return result
    }

    override fun toString(): String {
        return "CheckboxMetrics(" +
            "checkboxSize=$checkboxSize, " +
            "outlineCornerSize=$outlineCornerSize, " +
            "outlineFocusedCornerSize=$outlineFocusedCornerSize, " +
            "outlineSelectedCornerSize=$outlineSelectedCornerSize, " +
            "outlineSelectedFocusedCornerSize=$outlineSelectedFocusedCornerSize, " +
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
public class CheckboxIcons(public val checkbox: IconKey) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CheckboxIcons

        return checkbox == other.checkbox
    }

    override fun hashCode(): Int = checkbox.hashCode()

    override fun toString(): String = "CheckboxIcons(checkbox=$checkbox)"

    public companion object
}

public val LocalCheckboxStyle: ProvidableCompositionLocal<CheckboxStyle> = staticCompositionLocalOf {
    error("No CheckboxStyle provided. Have you forgotten the theme?")
}
