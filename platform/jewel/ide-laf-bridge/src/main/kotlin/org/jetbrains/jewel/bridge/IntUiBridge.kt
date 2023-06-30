package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.jewel.ButtonDefaults
import org.jetbrains.jewel.CheckboxDefaults
import org.jetbrains.jewel.IntelliJColors
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.buttonColors
import org.jetbrains.jewel.checkBoxColors
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.themes.intui.core.IntUiColorPalette
import javax.swing.UIManager

private val logger = Logger.getInstance("JewelIntUiBridge")

private val uiDefaults
    get() = UIManager.getDefaults()

internal fun bridgeIntUi(): IntelliJTheme {
    val isDark = StartupUiUtil.isUnderDarcula()

    return IntUiBridgeTheme(
        isDark,
        readIntUiColorPalette(isDark),
        readIntelliJColors(),
        readButtonDefaults(),
        readCheckboxDefaults(),
        groupHeaderDefaults,
        linkDefaults,
        textFieldDefaults,
        labelledTextFieldDefaults,
        textAreaDefaults,
        radioButtonDefaults,
        dropdownDefaults,
        contextMenuDefaults,
        defaultTextStyle,
        treeDefaults,
        chipDefaults,
        scrollThumbDefaults,
        progressBarDefaults,
    )
}

private fun readIntUiColorPalette(isDark: Boolean) =
    IntUiColorPalette(
        isDark = isDark,
        grey = readPaletteColors("Grey"),
        blue = readPaletteColors("Blue"),
        green = readPaletteColors("Green"),
        red = readPaletteColors("Red"),
        yellow = readPaletteColors("Yellow"),
        orange = readPaletteColors("Orange"),
        purple = readPaletteColors("Purple"),
        teal = readPaletteColors("Teal"),
    )

private fun readPaletteColors(colorName: String): List<Color> {
    val defaults = uiDefaults
    val allKeys = defaults.keys
    val colorNameLength = colorName.length

    val lastColorIndex = allKeys.asSequence()
        .filterIsInstance(String::class.java)
        .filter { it.startsWith(colorName) }
        .mapNotNull {
            val afterName = it.substring(colorNameLength)
            afterName.toIntOrNull()
        }
        .max()

    return buildList {
        for (i in 1..lastColorIndex) {
            val value = defaults["$colorName$i"] as? java.awt.Color
            if (value == null) {
                logger.error("Unable to find color value for palette key '$colorName$i'")
                continue
            }

            add(value.toComposeColor())
        }
    }
}

private fun readIntelliJColors() = IntelliJColors(
    foreground = retrieveColorOrUnspecified("Label.foreground"),
    background = retrieveColorOrUnspecified("Panel.background"),
    borderColor = retrieveColorOrUnspecified("Component.borderColor"),
    disabledForeground = retrieveColorOrUnspecified("Label.disabledForeground"),
    disabledBorderColor = retrieveColorOrUnspecified("Component.disabledBorderColor"),
)

// Hardcoded values come from DarculaButtonUI (they may be derived from multiple values)
private fun readButtonDefaults(): ButtonDefaults {
    val backgroundBrush = Brush.verticalGradient(retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground"))
    val contentColor = retrieveColorOrUnspecified("Button.foreground")
    val borderStroke = Stroke(
        width = 1.dp,
        brush = Brush.verticalGradient(retrieveColorsOrUnspecified("Button.startBorderColor", "Button.endBorderColor")),
        alignment = Stroke.Alignment.Center
    )
    val haloStroke = Stroke(
        width = retrieveIntAsDp("Component.focusWidth"),
        color = retrieveColorOrUnspecified("Component.focusColor"),
        alignment = Stroke.Alignment.Outside
    )

    val defaultBackgroundBrush = Brush.verticalGradient(retrieveColorsOrUnspecified("Button.default.startBackground", "Button.default.endBackground"))
    val defaultContentColor = retrieveColorOrUnspecified("Button.default.foreground")
    val defaultBorderStroke = Stroke(
        width = 1.dp,
        brush = Brush.verticalGradient(retrieveColorsOrUnspecified("Button.default.startBorderColor", "Button.default.endBorderColor")),
        alignment = Stroke.Alignment.Center
    )
    val defaultHaloStroke = Stroke(
        width = retrieveIntAsDp("Component.focusWidth"),
        color = retrieveColorOrUnspecified("Button.default.focusColor"),
        alignment = Stroke.Alignment.Outside
    )

    val transparentBrush = SolidColor(Color.Transparent)
    val disabledContentColor = retrieveColorOrUnspecified("Button.disabledText")
    val disabledBorderStroke = Stroke(
        width = 1.dp,
        brush = SolidColor(retrieveColorOrUnspecified("Button.disabledBorderColor")),
        alignment = Stroke.Alignment.Center
    )

    return ButtonDefaults(
        shape = RoundedCornerShape(retrieveIntAsDp("Button.arc") * 2), // Swing arcs are the diameter
        contentPadding = PaddingValues(horizontal = 16.dp),
        minWidth = 72.dp,
        minHeight = 24.dp, // From DarculaUIUtil#MINIMUM_HEIGHT
        outlinedButtonColors = buttonColors(
            backgroundBrush = backgroundBrush,
            contentColor = contentColor,
            borderStroke = borderStroke,
            disabledBackgroundBrush = transparentBrush,
            disabledContentColor = disabledContentColor,
            disabledBorderStroke = disabledBorderStroke,
            hoveredBackgroundBrush = backgroundBrush,
            hoveredContentColor = contentColor,
            hoveredBorderStroke = borderStroke,
            pressedBackgroundBrush = backgroundBrush,
            pressedContentColor = contentColor,
            pressedBorderStroke = borderStroke,
            focusedBackgroundBrush = backgroundBrush,
            focusedContentColor = contentColor,
            focusedBorderStroke = borderStroke,
            focusHaloStroke = haloStroke
        ),
        primaryButtonColors = buttonColors(
            backgroundBrush = defaultBackgroundBrush,
            contentColor = defaultContentColor,
            borderStroke = defaultBorderStroke,
            disabledBackgroundBrush = transparentBrush,
            disabledContentColor = disabledContentColor,
            disabledBorderStroke = disabledBorderStroke,
            hoveredBackgroundBrush = defaultBackgroundBrush,
            hoveredContentColor = defaultContentColor,
            hoveredBorderStroke = defaultBorderStroke,
            pressedBackgroundBrush = defaultBackgroundBrush,
            pressedContentColor = defaultContentColor,
            pressedBorderStroke = defaultBorderStroke,
            focusedBackgroundBrush = defaultBackgroundBrush,
            focusedContentColor = defaultContentColor,
            focusedBorderStroke = defaultBorderStroke,
            focusHaloStroke = defaultHaloStroke
        ),
    )
}

private fun readCheckboxDefaults() = CheckboxDefaults(
    colors = checkBoxColors(
        checkmarkTintColor = Color.Unspecified, // There is no tint for the checkmark icon
        contentColor = retrieveColorOrUnspecified("Checkbox.foreground"),
        uncheckedBackground = retrieveColorOrUnspecified("Checkbox.background"),
        uncheckedStroke = ,
        uncheckedFocusedStroke =,
        uncheckedFocusHoloStroke =,
        uncheckedErrorHoloStroke =,
        uncheckedHoveredBackground = retrieveColorOrUnspecified("Checkbox.background"),
        uncheckedHoveredStroke =,
        uncheckedDisabledBackground = retrieveColorOrUnspecified("Checkbox.background"),
        uncheckedDisabledStroke =,
        checkedBackground = retrieveColorOrUnspecified("Checkbox.background"),
        checkedStroke =,
        checkedFocusedStroke =,
        checkedFocusHoloStroke =,
        checkedErrorHoloStroke =,
        checkedHoveredBackground = retrieveColorOrUnspecified("Checkbox.background"),
        checkedHoveredStroke =,
        checkedDisabledBackground = retrieveColorOrUnspecified("Checkbox.background"),
        checkedDisabledStroke =,
        disabledCheckmarkColor =,
        disabledTextColor =,
    ),
    shape = RoundedCornerShape(),
    width =,
    height =,
    contentSpacing =,
    textStyle =,
    checkMarkOn =,
    checkMarkOff =,
    checkMarkIndeterminate =,
)
