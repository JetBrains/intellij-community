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

/** Combines the colors, metrics, and icons that define the visual style of a checkbox component. */
@Immutable
@GenerateDataFunctions
public class CheckboxStyle(
    /** The color tokens for the checkbox. */
    public val colors: CheckboxColors,
    /** The size and spacing metrics for the checkbox. */
    public val metrics: CheckboxMetrics,
    /** The icon keys for the checkbox. */
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

    /** Companion object for [CheckboxStyle]. */
    public companion object
}

/** Holds color tokens for the checkbox component in its various states (default, disabled, selected). */
@Immutable
@GenerateDataFunctions
public class CheckboxColors(
    /** The content (label) color. */
    public val content: Color,
    /** The content color when the checkbox is disabled. */
    public val contentDisabled: Color,
    /** The content color when the checkbox is selected. */
    public val contentSelected: Color,
) {
    /**
     * Returns a [State] holding the content color appropriate for the given [state], reflecting disabled and selected
     * variants.
     */
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

    /** Companion object for [CheckboxColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the checkbox component, including checkbox size, outline sizes, corner sizes, and
 * content gap.
 */
@Immutable
@GenerateDataFunctions
public class CheckboxMetrics(
    /** The size of the checkbox indicator box. */
    public val checkboxSize: DpSize,
    /** The corner size of the error/warning outline in the default (unfocused, unselected) state. */
    public val outlineCornerSize: CornerSize,
    /** The corner size of the outline when the checkbox is focused (and not selected). */
    public val outlineFocusedCornerSize: CornerSize,
    /** The corner size of the outline when the checkbox is selected or indeterminate (and not focused). */
    public val outlineSelectedCornerSize: CornerSize,
    /** The corner size of the outline when the checkbox is focused and either selected or indeterminate. */
    public val outlineSelectedFocusedCornerSize: CornerSize,
    /** The size of the outline box in the default state, when the checkbox is neither focused nor selected. */
    public val outlineSize: DpSize,
    /** The size of the focus outline when the checkbox is focused. */
    public val outlineFocusedSize: DpSize,
    /**
     * The size of the outline box when the checkbox is selected or indeterminate and not focused. This box carries the
     * validation (Error/Warning) outline.
     */
    public val outlineSelectedSize: DpSize,
    /** The size of the outline box when the checkbox is both selected (or indeterminate) and focused. */
    public val outlineSelectedFocusedSize: DpSize,
    /** The gap between the checkbox icon and its label. */
    public val iconContentGap: Dp,
) {
    /**
     * Returns a [State] holding the outline corner size appropriate for the given [state], varying by focus and
     * selection.
     */
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

    /** Returns a [State] holding the outline size appropriate for the given [state], varying by focus and selection. */
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

    /** Companion object for [CheckboxMetrics]. */
    public companion object
}

/** Holds the icon key for the checkbox component. */
@Immutable
@GenerateDataFunctions
public class CheckboxIcons(
    /** The icon key for the checkbox indicator. */
    public val checkbox: IconKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CheckboxIcons

        return checkbox == other.checkbox
    }

    override fun hashCode(): Int = checkbox.hashCode()

    override fun toString(): String = "CheckboxIcons(checkbox=$checkbox)"

    /** Companion object for [CheckboxIcons]. */
    public companion object
}

/** CompositionLocal used to provide the [CheckboxStyle] to checkbox components. */
public val LocalCheckboxStyle: ProvidableCompositionLocal<CheckboxStyle> = staticCompositionLocalOf {
    error("No CheckboxStyle provided. Have you forgotten the theme?")
}
