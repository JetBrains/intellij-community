package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.BorderColors
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.OutlineColors

@Immutable
internal class BridgeGlobalColors(
    override val borders: BorderColors,
    override val outlines: OutlineColors,
    @SwingLafKey("*.infoForeground") override val infoContent: Color,
    @SwingLafKey("Panel.background") override val paneBackground: Color,
) : GlobalColors {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BridgeGlobalColors

        if (borders != other.borders) return false
        if (outlines != other.outlines) return false
        if (infoContent != other.infoContent) return false
        if (paneBackground != other.paneBackground) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borders.hashCode()
        result = 31 * result + outlines.hashCode()
        result = 31 * result + infoContent.hashCode()
        result = 31 * result + paneBackground.hashCode()
        return result
    }

    override fun toString(): String =
        "BridgeGlobalColors(borders=$borders, outlines=$outlines, infoContent=$infoContent, paneBackground=$paneBackground)"

    companion object {

        fun readFromLaF() = BridgeGlobalColors(
            borders = BridgeBorderColors.readFromLaF(),
            outlines = BridgeOutlineColors.readFromLaF(),
            infoContent = retrieveColorOrUnspecified("*.infoForeground"),
            paneBackground = retrieveColorOrUnspecified("Panel.background"),
        )
    }
}

@Immutable
internal class BridgeBorderColors(
    @SwingLafKey("Component.borderColor") override val normal: Color,
    @SwingLafKey("Component.focusedBorderColor") override val focused: Color,
    @SwingLafKey("*.disabledBorderColor") override val disabled: Color,
) : BorderColors {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BridgeBorderColors

        if (normal != other.normal) return false
        if (focused != other.focused) return false
        if (disabled != other.disabled) return false

        return true
    }

    override fun hashCode(): Int {
        var result = normal.hashCode()
        result = 31 * result + focused.hashCode()
        result = 31 * result + disabled.hashCode()
        return result
    }

    override fun toString(): String =
        "BridgeBorderColors(normal=$normal, focused=$focused, disabled=$disabled)"

    companion object {

        fun readFromLaF() = BridgeBorderColors(
            normal = retrieveColorOrUnspecified("Component.borderColor"),
            focused = retrieveColorOrUnspecified("Component.focusedBorderColor"),
            disabled = retrieveColorOrUnspecified("*.disabledBorderColor"),
        )
    }
}

@Immutable
internal class BridgeOutlineColors(
    @SwingLafKey("*.focusColor") override val focused: Color,
    @SwingLafKey("Component.warningFocusColor") override val focusedWarning: Color,
    @SwingLafKey("Component.errorFocusColor") override val focusedError: Color,
    @SwingLafKey("Component.inactiveWarningFocusColor") override val warning: Color,
    @SwingLafKey("Component.inactiveErrorFocusColor") override val error: Color,
) : OutlineColors {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BridgeOutlineColors

        if (focused != other.focused) return false
        if (focusedWarning != other.focusedWarning) return false
        if (focusedError != other.focusedError) return false
        if (warning != other.warning) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = focused.hashCode()
        result = 31 * result + focusedWarning.hashCode()
        result = 31 * result + focusedError.hashCode()
        result = 31 * result + warning.hashCode()
        result = 31 * result + error.hashCode()
        return result
    }

    override fun toString(): String =
        "BridgeOutlineColors(focused=$focused, focusedWarning=$focusedWarning, focusedError=$focusedError, " +
            "warning=$warning, error=$error)"

    companion object {

        fun readFromLaF() = BridgeOutlineColors(
            focused = retrieveColorOrUnspecified("*.focusColor"),
            focusedWarning = retrieveColorOrUnspecified("Component.warningFocusColor"),
            focusedError = retrieveColorOrUnspecified("Component.errorFocusColor"),
            warning = retrieveColorOrUnspecified("Component.inactiveWarningFocusColor"),
            error = retrieveColorOrUnspecified("Component.inactiveErrorFocusColor"),
        )
    }
}
