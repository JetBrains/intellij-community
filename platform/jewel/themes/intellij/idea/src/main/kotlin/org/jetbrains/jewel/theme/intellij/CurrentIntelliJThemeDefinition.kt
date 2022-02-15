package org.jetbrains.jewel.theme.intellij

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking

@Suppress("FunctionName")
fun CurrentIntelliJThemeDefinition(): IntelliJThemeDefinition {
    val buttonPalette = IntelliJPalette.Button(
        background = Brush.verticalGradient(retrieveColors("Button.startBackground", "Button.endBackground")),
        foreground = retrieveColor("Button.foreground"),
        foregroundDisabled = retrieveColor("Button.disabledText"),
        shadow = retrieveColorOrNull("Button.default.shadowColor") ?: Color.Unspecified,
        stroke = Brush.verticalGradient(retrieveColors("Button.startBorderColor", "Button.endBorderColor")),
        strokeFocused = retrieveColor("Button.focusedBorderColor"),
        strokeDisabled = retrieveColor("Button.disabledBorderColor"),
        defaultBackground = Brush.verticalGradient(
            retrieveColors(
                "Button.default.startBackground",
                "Button.default.endBackground"
            )
        ),
        defaultForeground = retrieveColor("Button.default.foreground"),
        defaultStroke = Brush.verticalGradient(
            retrieveColors(
                "Button.default.startBorderColor",
                "Button.default.endBorderColor"
            )
        ),
        defaultStrokeFocused = retrieveColor("Button.default.focusedBorderColor"),
        defaultShadow = retrieveColorOrNull("Button.default.shadowColor") ?: Color.Unspecified
    )

    val textFieldPalette = IntelliJPalette.TextField(
        background = retrieveColor("TextField.background"),
        backgroundDisabled = retrieveColor("TextField.disabledBackground"),
        foreground = retrieveColor("TextField.foreground"),
        foregroundDisabled = retrieveColor("Label.disabledForeground")
    )

    val palette = IntelliJPalette(
        button = buttonPalette,
        background = retrieveColor("Panel.background"),
        text = retrieveColor("Panel.foreground"),
        textDisabled = retrieveColor("Label.disabledForeground"),
        controlStroke = retrieveColor("Component.borderColor"),
        controlStrokeDisabled = retrieveColor("Component.disabledBorderColor"),
        controlStrokeFocused = retrieveColor("Component.focusedBorderColor"),
        controlFocusHalo = retrieveColor("Component.focusColor"),
        controlInactiveHaloError = retrieveColor("Component.inactiveErrorFocusColor"),
        controlInactiveHaloWarning = retrieveColor("Component.inactiveWarningFocusColor"),
        controlHaloError = retrieveColor("Component.errorFocusColor"),
        controlHaloWarning = retrieveColor("Component.warningFocusColor"),
        checkbox = IntelliJPalette.Checkbox(
            background = retrieveColor("CheckBox.background"),
            foreground = retrieveColor("CheckBox.foreground"),
            foregroundDisabled = retrieveColor("CheckBox.disabledText")
        ),
        radioButton = IntelliJPalette.RadioButton(
            background = retrieveColor("RadioButton.background"),
            foreground = retrieveColor("RadioButton.foreground"),
            foregroundDisabled = retrieveColor("RadioButton.disabledText")
        ),
        textField = textFieldPalette,
        separator = IntelliJPalette.Separator(
            color = retrieveColor("Separator.foreground"),
            background = retrieveColor("Separator.background")
        ),
        scrollbar = IntelliJPalette.Scrollbar(
            thumbHoverColor = retrieveColor("ScrollBar.foreground"),
            thumbIdleColor = retrieveColor("ScrollBar.thumbHighlight")
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
