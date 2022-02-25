package org.jetbrains.jewel.theme.idea

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.theme.intellij.IntelliJMetrics
import org.jetbrains.jewel.theme.intellij.IntelliJPainters
import org.jetbrains.jewel.theme.intellij.IntelliJPalette
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDefinition
import org.jetbrains.jewel.theme.intellij.IntelliJTypography

@Suppress("FunctionName")
fun CurrentIntelliJThemeDefinition(): IntelliJThemeDefinition {

    val buttonPalette = IntelliJPalette.Button(
        background = Brush.verticalGradient(retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground")),
        foreground = retrieveColorOrUnspecified("Button.foreground"),
        foregroundDisabled = retrieveColorOrUnspecified("Button.disabledText"),
        shadow = retrieveColorOrUnspecified("Button.default.shadowColor"),
        stroke = Brush.verticalGradient(retrieveColorsOrUnspecified("Button.startBorderColor", "Button.endBorderColor")),
        strokeFocused = retrieveColorOrUnspecified("Button.focusedBorderColor"),
        strokeDisabled = retrieveColorOrUnspecified("Button.disabledBorderColor"),
        defaultBackground = Brush.verticalGradient(
            retrieveColorsOrUnspecified(
                "Button.default.startBackground",
                "Button.default.endBackground"
            )
        ),
        defaultForeground = retrieveColorOrUnspecified("Button.default.foreground"),
        defaultStroke = Brush.verticalGradient(
            retrieveColorsOrUnspecified(
                "Button.default.startBorderColor",
                "Button.default.endBorderColor"
            )
        ),
        defaultStrokeFocused = retrieveColorOrUnspecified("Button.default.focusedBorderColor"),
        defaultShadow = retrieveColorOrUnspecified("Button.default.shadowColor")
    )

    val textFieldPalette = IntelliJPalette.TextField(
        background = retrieveColorOrUnspecified("TextField.background"),
        backgroundDisabled = retrieveColorOrUnspecified("TextField.disabledBackground"),
        foreground = retrieveColorOrUnspecified("TextField.foreground"),
        foregroundDisabled = retrieveColorOrUnspecified("Label.disabledForeground")
    )

    val palette = IntelliJPalette(
        button = buttonPalette,
        background = retrieveColorOrUnspecified("Panel.background"),
        text = retrieveColorOrUnspecified("Panel.foreground"),
        textDisabled = retrieveColorOrUnspecified("Label.disabledForeground"),
        controlStroke = retrieveColorOrUnspecified("Component.borderColor"),
        controlStrokeDisabled = retrieveColorOrUnspecified("Component.disabledBorderColor"),
        controlStrokeFocused = retrieveColorOrUnspecified("Component.focusedBorderColor"),
        controlFocusHalo = retrieveColorOrUnspecified("Component.focusColor"),
        controlInactiveHaloError = retrieveColorOrUnspecified("Component.inactiveErrorFocusColor"),
        controlInactiveHaloWarning = retrieveColorOrUnspecified("Component.inactiveWarningFocusColor"),
        controlHaloError = retrieveColorOrUnspecified("Component.errorFocusColor"),
        controlHaloWarning = retrieveColorOrUnspecified("Component.warningFocusColor"),
        checkbox = IntelliJPalette.Checkbox(
            background = retrieveColorOrUnspecified("CheckBox.background"),
            foreground = retrieveColorOrUnspecified("CheckBox.foreground"),
            foregroundDisabled = retrieveColorOrUnspecified("CheckBox.disabledText")
        ),
        radioButton = IntelliJPalette.RadioButton(
            background = retrieveColorOrUnspecified("RadioButton.background"),
            foreground = retrieveColorOrUnspecified("RadioButton.foreground"),
            foregroundDisabled = retrieveColorOrUnspecified("RadioButton.disabledText")
        ),
        textField = textFieldPalette,
        separator = IntelliJPalette.Separator(
            color = retrieveColorOrUnspecified("Separator.foreground"),
            background = retrieveColorOrUnspecified("Separator.background")
        ),
        scrollbar = IntelliJPalette.Scrollbar(
            thumbHoverColor = retrieveColorOrUnspecified("ScrollBar.foreground"),
            thumbIdleColor = retrieveColorOrUnspecified("ScrollBar.thumbHighlight")
        )
    )

    val metrics = IntelliJMetrics(
        gridSize = 8.dp,
        singlePadding = 8.dp,
        doublePadding = 16.dp,
        controlFocusHaloWidth = retrieveIntAsDp("Component.focusWidth"),
        controlArc = retrieveIntAsDp("Component.arc"),
        button = IntelliJMetrics.Button(
            strokeWidth = 1.dp,
            arc = CornerSize(retrieveIntAsDp("Button.arc")),
            padding = retrieveInsetsAsPaddingValues("Button.margin"),
        ),
        controlFocusHaloArc = retrieveIntAsDp("Component.arc"),
        separator = IntelliJMetrics.Separator(
            strokeWidth = 1.dp
        ),
        scrollbar = IntelliJMetrics.Scrollbar(
            minSize = 29.dp,
            thickness = 7.dp,
            thumbCornerSize = CornerSize(4.dp)
        )
    )

    val painters = IntelliJPainters(
        checkbox = IntelliJPainters.CheckboxPainters(
            unselected = lookupSvgIcon(name = "checkBox", selected = false, focused = false, enabled = true),
            unselectedDisabled = lookupSvgIcon(name = "checkBox", selected = false, focused = false, enabled = false),
            unselectedFocused = lookupSvgIcon(name = "checkBox", selected = false, focused = true, enabled = true),
            selected = lookupSvgIcon(name = "checkBox", selected = true, focused = false, enabled = true),
            selectedDisabled = lookupSvgIcon(name = "checkBox", selected = true, focused = false, enabled = false),
            selectedFocused = lookupSvgIcon(name = "checkBox", selected = true, focused = true, enabled = true),
            indeterminate = lookupSvgIcon(
                name = "checkBoxIndeterminate",
                selected = true,
                focused = false,
                enabled = true
            ),
            indeterminateDisabled = lookupSvgIcon(
                name = "checkBoxIndeterminate",
                selected = true,
                focused = false,
                enabled = false
            ),
            indeterminateFocused = lookupSvgIcon(
                name = "checkBoxIndeterminate",
                selected = true,
                focused = true,
                enabled = true
            )
        ),
        radioButton = IntelliJPainters.RadioButtonPainters(
            unselected = lookupSvgIcon(name = "radio", selected = false, focused = false, enabled = true),
            unselectedDisabled = lookupSvgIcon(name = "radio", selected = false, focused = false, enabled = false),
            unselectedFocused = lookupSvgIcon(name = "radio", selected = false, focused = true, enabled = true),
            selected = lookupSvgIcon(name = "radio", selected = true, focused = false, enabled = true),
            selectedDisabled = lookupSvgIcon(name = "radio", selected = true, focused = false, enabled = false),
            selectedFocused = lookupSvgIcon(name = "radio", selected = true, focused = true, enabled = true)
        )
    )

    val typography = runBlocking {
        IntelliJTypography(
            default = retrieveFont("Panel.font", palette.text),
            button = retrieveFont("Button.font", palette.button.foreground),
            checkBox = retrieveFont("CheckBox.font", palette.checkbox.foreground),
            radioButton = retrieveFont("RadioButton.font", palette.radioButton.foreground),
            textField = retrieveFont("TextField.font", palette.textField.foreground)
        )
    }

    return IntelliJThemeDefinition(
        palette = palette,
        metrics = metrics,
        typography = typography,
        painters = painters
    )
}