package org.jetbrains.jewel.themes.darcula.idebridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.IntelliJPainters
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.IntelliJThemeDefinition
import org.jetbrains.jewel.IntelliJTypography

@Suppress("FunctionName")
suspend fun CurrentIntelliJThemeDefinition(): IntelliJThemeDefinition {
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
        isLight = JBColor.isBright(),
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
            color = retrieveColorOrUnspecified("Separator.separatorColor"),
            background = retrieveColorOrUnspecified("Separator.background")
        ),
        scrollbar = IntelliJPalette.Scrollbar(
            thumbHoverColor = retrieveColorOrUnspecified("ScrollBar.track"),
            thumbIdleColor = retrieveColorOrUnspecified("ScrollBar.foreground").copy(alpha = 0.12f)
        ),
        tab = IntelliJPalette.Tab(
            underlineColor = retrieveColorOrUnspecified("TabbedPane.underlineColor"),
            hoveredBackgroundColor = retrieveColorOrUnspecified("TabbedPane.hoverColor"),
            tabSelectionHeight = retrieveIntAsDp("TabbedPane.tabSelectionHeight")
        ),
        treeView = IntelliJPalette.TreeView(
            focusedSelectedElementBackground = retrieveColorOrUnspecified("Tree.selectionBackground"),
            background = retrieveColorOrUnspecified("Tree.background")
        ),
        slider = IntelliJPalette.Slider(
            background = retrieveColorOrUnspecified("Slider.background"),
            foreground = retrieveColorOrUnspecified("Slider.foreground")
        )
    )

    val metrics = IntelliJMetrics(
        singlePadding = 8.dp,
        controlFocusHaloWidth = retrieveIntAsDp("Component.focusWidth"),
        controlFocusHaloArc = retrieveIntAsDp("Component.arc"),
        controlArc = retrieveIntAsDp("Component.arc"),
        button = IntelliJMetrics.Button(
            strokeWidth = 1.dp,
            arc = CornerSize(retrieveIntAsDp("Button.arc")),
            padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ),
        separator = IntelliJMetrics.Separator(
            strokeWidth = 1.dp
        ),
        scrollbar = IntelliJMetrics.Scrollbar(
            minSize = 29.dp,
            thickness = 7.dp,
            thumbCornerSize = CornerSize(4.dp)
        ),
        treeView = IntelliJMetrics.TreeView(
            indentWidth = retrieveIntAsDp("Tree.leftChildIndent"),
            arrowEndPadding = retrieveIntAsDp("Tree.rightChildIndent")
        )
    )

    val painters = IntelliJPainters(
        checkbox = IntelliJPainters.CheckboxPainters(
            unselected = lookupIJSvgIcon(name = "checkBox", selected = false, focused = false, enabled = true),
            unselectedDisabled = lookupIJSvgIcon(name = "checkBox", selected = false, focused = false, enabled = false),
            unselectedFocused = lookupIJSvgIcon(name = "checkBox", selected = false, focused = true, enabled = true),
            selected = lookupIJSvgIcon(name = "checkBox", selected = true, focused = false, enabled = true),
            selectedDisabled = lookupIJSvgIcon(name = "checkBox", selected = true, focused = false, enabled = false),
            selectedFocused = lookupIJSvgIcon(name = "checkBox", selected = true, focused = true, enabled = true),
            indeterminate = lookupIJSvgIcon(
                name = "checkBoxIndeterminate",
                selected = true,
                focused = false,
                enabled = true
            ),
            indeterminateDisabled = lookupIJSvgIcon(
                name = "checkBoxIndeterminate",
                selected = true,
                focused = false,
                enabled = false
            ),
            indeterminateFocused = lookupIJSvgIcon(
                name = "checkBoxIndeterminate",
                selected = true,
                focused = true,
                enabled = true
            )
        ),
        radioButton = IntelliJPainters.RadioButtonPainters(
            unselected = lookupIJSvgIcon(name = "radio", selected = false, focused = false, enabled = true),
            unselectedDisabled = lookupIJSvgIcon(name = "radio", selected = false, focused = false, enabled = false),
            unselectedFocused = lookupIJSvgIcon(name = "radio", selected = false, focused = true, enabled = true),
            selected = lookupIJSvgIcon(name = "radio", selected = true, focused = false, enabled = true),
            selectedDisabled = lookupIJSvgIcon(name = "radio", selected = true, focused = false, enabled = false),
            selectedFocused = lookupIJSvgIcon(name = "radio", selected = true, focused = true, enabled = true)
        ),
        treeView = IntelliJPainters.TreeViewPainters(
            arrow = { rememberSvgResource("general/chevron-right.svg", AllIcons::class.java.classLoader) }
        )
    )

    val typography = IntelliJTypography(
        default = retrieveFont("Panel.font", palette.text),
        button = retrieveFont("Button.font", palette.button.foreground),
        checkBox = retrieveFont("CheckBox.font", palette.checkbox.foreground),
        radioButton = retrieveFont("RadioButton.font", palette.radioButton.foreground),
        textField = retrieveFont("TextField.font", palette.textField.foreground),
        slider = retrieveFont("Slider.font", palette.slider.foreground)
    )

    return IntelliJThemeDefinition(
        palette = palette,
        metrics = metrics,
        typography = typography,
        painters = painters
    )
}
