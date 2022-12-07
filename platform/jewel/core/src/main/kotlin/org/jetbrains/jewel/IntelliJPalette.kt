package org.jetbrains.jewel

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

data class IntelliJPalette(
    val isLight: Boolean,

    val button: Button,
    val checkbox: Checkbox,
    val radioButton: RadioButton,
    val textField: TextField,
    val separator: Separator,
    val treeView: TreeView,
    val slider: Slider,

    val background: Color, // Panel.background

    val text: Color, // Panel.foreground
    val textDisabled: Color, // Label.disabledForeground

    val controlStroke: Color, // Component.borderColor
    val controlStrokeDisabled: Color, // Component.disabledBorderColor
    val controlStrokeFocused: Color, // Component.focusedBorderColor

    val controlFocusHalo: Color, // Component.focusColor
    val controlInactiveHaloError: Color, // Component.inactiveErrorFocusColor
    val controlInactiveHaloWarning: Color, // Component.inactiveWarningFocusColor
    val controlHaloError: Color, // Component.errorFocusColor
    val controlHaloWarning: Color, // Component.warningFocusColor
    val scrollbar: Scrollbar,
    val tab: Tab
) {

    data class Slider(
        val foreground: Color,
        val background: Color
    ) {

        companion object
    }

    data class TreeView(
        val focusedSelectedElementBackground: Color,
        val background: Color
    ) {

        companion object
    }

    data class RadioButton(
        val background: Color,
        val foreground: Color,
        val foregroundDisabled: Color
    ) {

        companion object
    }

    data class Checkbox(
        val background: Color, // Checkbox.background
        val foreground: Color,
        val foregroundDisabled: Color
    ) {

        companion object
    }

    data class TextField(
        val background: Color,
        val backgroundDisabled: Color,
        val foreground: Color,
        val foregroundDisabled: Color
    ) {

        companion object
    }

    data class Button(
        val background: Brush, // Button.startBackground and Button.endBackground
        val foreground: Color, // Button.foreground
        val foregroundDisabled: Color, // Button.disabledText
        val shadow: Color, // Button.default.shadowColor
        val stroke: Brush, // Button.startBorderColor and Button.endBorderColor
        val strokeFocused: Color, // Button.focusedBorderColor
        val strokeDisabled: Color, // Button.disabledBorderColor

        val defaultBackground: Brush, // Button.default.startBackground and Button.default.endBackground
        val defaultForeground: Color, // Button.default.foreground
        val defaultStroke: Brush, // Button.default.startBorderColor and Button.default.endBorderColor
        val defaultStrokeFocused: Color, // Button.default.focusedBorderColor
        val defaultShadow: Color // Button.default.shadowColor
    ) {

        companion object
    }

    data class Separator(
        val color: Color, // Separator.separatorColor
        val background: Color // Separator.background
    ) {

        companion object
    }

    data class Scrollbar(
        val thumbHoverColor: Color, // See com.intellij.ui.components.ScrollBarPainter.THUMB_BACKGROUND
        val thumbIdleColor: Color // See com.intellij.ui.components.ScrollBarPainter.THUMB_HOVERED_BACKGROUND
    ) {

        companion object
    }

    data class Tab(
        val underlineColor: Color,
        val hoveredBackgroundColor: Color,
        val tabSelectionHeight: Dp
    ) {

        companion object
    }

    companion object
}
