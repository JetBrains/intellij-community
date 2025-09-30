// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.awt.datatransfer.StringSelection
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.graphics.cssLinearGradient
import org.jetbrains.jewel.ui.typography
import org.jetbrains.jewel.ui.util.fromArgbHexStringOrNull
import org.jetbrains.jewel.ui.util.toArgbHexString

private val firstColumnWidth = 100.dp

@Composable
internal fun BrushesShowcase() {
    val angleDegrees = rememberTextFieldState("15.00")
    val scaleXState = rememberTextFieldState("1.0")
    val scaleYState = rememberTextFieldState("1.0")
    val offsetXState = rememberTextFieldState("0.0")
    val offsetYState = rememberTextFieldState("0.0")

    val angleIsFloat by remember { derivedStateOf { angleDegrees.text.toString().toFloatOrNull() != null } }
    val scaleX by remember { derivedStateOf { scaleXState.text.toString().toFloatOrNull() ?: 0f } }
    val scaleY by remember { derivedStateOf { scaleYState.text.toString().toFloatOrNull() ?: 0f } }
    val offsetX by remember { derivedStateOf { offsetXState.text.toString().toFloatOrNull() ?: 0f } }
    val offsetY by remember { derivedStateOf { offsetYState.text.toString().toFloatOrNull() ?: 0f } }

    val colorsState = rememberTextFieldState("F00 0F0 00F")
    val stopsState = rememberTextFieldState("0.0 0.5 1.0")

    val parsedColors by
        remember(colorsState.text) {
            derivedStateOf { colorsState.text.splitNonEmpty(" ").mapNotNull { Color.fromArgbHexStringOrNull(it) } }
        }
    val parsedStops by
        remember(stopsState.text) {
            derivedStateOf { stopsState.text.splitNonEmpty(" ").mapNotNull { it.toFloatOrNull() } }
        }

    val colorsAreValid by
        remember(parsedColors, colorsState.text) {
            derivedStateOf {
                val colorStrings = colorsState.text.splitNonEmpty(" ")
                parsedColors.size == colorStrings.size
            }
        }

    val stopsAreValid by
        remember(parsedStops, stopsState.text) {
            derivedStateOf {
                val stopStrings = stopsState.text.splitNonEmpty(" ")
                val stops = parsedStops
                stops.size == stopStrings.size && stops.all { it in 0f..1f }
            }
        }

    val stopsAndColorsMatch by remember { derivedStateOf { parsedStops.size == parsedColors.size } }
    val canShowGradient by remember {
        derivedStateOf { angleIsFloat && colorsAreValid && stopsAreValid && stopsAndColorsMatch }
    }

    val brush =
        remember(angleDegrees.text, parsedColors, parsedStops, scaleX, scaleY, offsetX, offsetY, canShowGradient) {
            Brush.cssLinearGradient(
                angleDegrees = angleDegrees.text.toString().toDoubleOrNull() ?: 0.0,
                colors = parsedColors.takeIf { canShowGradient } ?: listOf(Color.Transparent, Color.Transparent),
                stops = parsedStops.takeIf { canShowGradient } ?: listOf(0f, 1f),
                scaleX = scaleX,
                scaleY = scaleY,
                offset = Offset(offsetX, offsetY),
            )
        }

    val clipboard = LocalClipboard.current

    VerticallyScrollableContainer {
        Column(
            modifier = Modifier.padding(end = scrollbarContentSafePadding(), bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "CSS-like Linear Gradient",
                    style = JewelTheme.typography.h2TextStyle,
                    modifier = Modifier.weight(1f),
                )
                Link(
                    text = "Copy to clipboard",
                    onClick = {
                        copyBrushInvocationToClipboard(
                            parsedColors,
                            parsedStops,
                            angleDegrees,
                            scaleX,
                            scaleY,
                            offsetX,
                            offsetY,
                            clipboard,
                        )
                    },
                    enabled = canShowGradient,
                )
                Link(
                    "Reset",
                    onClick = {
                        angleDegrees.setTextAndPlaceCursorAtEnd("15.00")
                        scaleXState.setTextAndPlaceCursorAtEnd("1.0")
                        scaleYState.setTextAndPlaceCursorAtEnd("1.0")
                        offsetXState.setTextAndPlaceCursorAtEnd("0.0")
                        offsetYState.setTextAndPlaceCursorAtEnd("0.0")
                        colorsState.setTextAndPlaceCursorAtEnd("F00 0F0 00F")
                        stopsState.setTextAndPlaceCursorAtEnd("0.0 0.5 1.0")
                    },
                )
            }

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(firstColumnWidth)
                        .border(
                            Stroke.Alignment.Inside,
                            1.dp,
                            JewelTheme.globalColors.borders.normal,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(1.dp)
                        .background(brush, RoundedCornerShape(7.dp))
            ) {
                if (!canShowGradient) {
                    Text(
                        "Invalid gradient",
                        modifier = Modifier.align(Alignment.Center),
                        color = JewelTheme.globalColors.text.info,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RotaryControl("Angle (deg)", angleDegrees)
                SliderControl("Scale X", scaleXState, 0.1f, 10f)
                SliderControl("Scale Y", scaleYState, 0.1f, 10f)
                SliderControl("Offset X", offsetXState, -10f, 10f)
                SliderControl("Offset Y", offsetYState, -10f, 10f)
                SpaceSeparatedValues(
                    state = colorsState,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Colors",
                    outline = if (colorsAreValid && stopsAndColorsMatch) Outline.None else Outline.Error,
                    inputTransformation = spaceSeparatedColorsInputTransformation,
                )
                SpaceSeparatedValues(
                    state = stopsState,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Stops",
                    outline = if (stopsAreValid && stopsAndColorsMatch) Outline.None else Outline.Error,
                    inputTransformation = spaceSeparatedFloatsInputTransformation,
                )
                if (!stopsAndColorsMatch) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.width(firstColumnWidth))
                        Text(
                            "Stops and colors must have the same number of elements",
                            color = JewelTheme.globalColors.text.error,
                        )
                    }
                }
            }
        }
    }
}

private fun copyBrushInvocationToClipboard(
    parsedColors: List<Color>,
    parsedStops: List<Float>,
    angleDegrees: TextFieldState,
    scaleX: Float,
    scaleY: Float,
    offsetX: Float,
    offsetY: Float,
    clipboard: Clipboard,
) {
    val colorStrings =
        parsedColors.joinToString(", ") {
            val formattedColor = it.toArgbHexString(includeHashSymbol = false).removePrefix("#").uppercase()

            "Color(0x$formattedColor)"
        }
    val stopStrings = parsedStops.joinToString(", ") { "${it}f" }
    val code =
        """
        |Brush.cssLinearGradient(
        |    angleDegrees = ${angleDegrees.text},
        |    colors = listOf($colorStrings),
        |    stops = listOf($stopStrings),
        |    scaleX = ${scaleX}f,
        |    scaleY = ${scaleY}f,
        |    offset = Offset(${offsetX}f, ${offsetY}f),
        |)
        """
            .trimMargin()

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
        // On desktop the underlying clipboard APIs are synchronous (AWT/IJP clipboard)
        clipboard.setClipEntry(ClipEntry(StringSelection(code)))
    }
}

private fun CharSequence.splitNonEmpty(delimiter: String) = split(delimiter).filter { it.isNotBlank() }

private val textFieldWidth = 80.dp

@Composable
private fun RotaryControl(label: String, state: TextFieldState) {
    val value by remember { derivedStateOf { state.text.toString().toDoubleOrNull() ?: 0.0 } }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(firstColumnWidth))
        TextField(
            state = state,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(textFieldWidth),
            inputTransformation = remember { floatInputTransformation() },
        )
        Rotary(
            value,
            onValueChange = { state.setTextAndPlaceCursorAtEnd(String.format(Locale.getDefault(), "%.2f", it)) },
            Modifier.size(36.dp),
        )
    }
}

private fun floatInputTransformation(min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE) =
    InputTransformation.byValue { oldText, newText ->
        val text = newText.toString()
        if (text.isEmpty() || text == "-") return@byValue newText

        val float = text.toFloatOrNull() ?: "${text}0".toFloatOrNull()
        if (float == null || float !in min..max) {
            oldText
        } else {
            newText
        }
    }

@Composable
private fun SliderControl(label: String, state: TextFieldState, min: Float, max: Float) {
    val value by remember { derivedStateOf { state.text.toString().toFloatOrNull() ?: min } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(firstColumnWidth))
        TextField(
            state = state,
            modifier = Modifier.width(textFieldWidth),
            inputTransformation = remember(min, max) { floatInputTransformation(min, max) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Slider(
            value = value,
            onValueChange = { state.setTextAndPlaceCursorAtEnd(String.format(Locale.getDefault(), "%.2f", it)) },
            modifier = Modifier.weight(1f),
            valueRange = min..max,
        )
    }
}

@Composable
private fun SpaceSeparatedValues(
    state: TextFieldState,
    label: String,
    outline: Outline,
    modifier: Modifier = Modifier,
    inputTransformation: InputTransformation? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(firstColumnWidth))
        TextField(state, modifier = Modifier.weight(1f), outline = outline, inputTransformation = inputTransformation)
    }
}

private val spaceSeparatedColorsInputTransformation =
    InputTransformation.byValue { oldText, newText ->
        newText.takeIf {
            val text = it.toString()
            val validColorsCount =
                text
                    .splitNonEmpty(" ")
                    .mapNotNull { colorHex ->
                        // We allow valid hex colours, and strings as long as 8 chars that are valid hex
                        Color.fromArgbHexStringOrNull(colorHex)
                            ?: colorHex
                                .removePrefix("#")
                                // Ignore the hash prefix and try parsing as normal hex int to allow intermediate values
                                .takeIf { trimmed -> trimmed.length <= 8 }
                                ?.toIntOrNull(16)
                    }
                    .size
            val allSplitsCount = text.splitNonEmpty(" ").size
            validColorsCount == allSplitsCount
        } ?: oldText
    }

private val spaceSeparatedFloatsInputTransformation =
    InputTransformation.byValue { oldText, newText ->
        newText.takeIf { value ->
            val text = value.toString()
            val validFloatsCount = text.splitNonEmpty(" ").mapNotNull { it.toFloatOrNull() }.size
            val allSplitsCount = text.splitNonEmpty(" ").size
            validFloatsCount == allSplitsCount
        } ?: oldText
    }
