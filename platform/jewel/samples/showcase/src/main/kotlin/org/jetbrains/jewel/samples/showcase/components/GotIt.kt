// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InfoText
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.gotit.GotItBalloonPosition
import org.jetbrains.jewel.ui.component.gotit.GotItBody
import org.jetbrains.jewel.ui.component.gotit.GotItButton
import org.jetbrains.jewel.ui.component.gotit.GotItButtons
import org.jetbrains.jewel.ui.component.gotit.GotItIconOrStep
import org.jetbrains.jewel.ui.component.gotit.GotItImage
import org.jetbrains.jewel.ui.component.gotit.GotItLink
import org.jetbrains.jewel.ui.component.gotit.GotItTooltip
import org.jetbrains.jewel.ui.component.gotit.buildGotItBody
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private const val JEWEL_README = "https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/README.md"

private enum class TimeoutUnit {
    MILLISECONDS,
    SECONDS,
    MINUTES,
}

@Composable
internal fun GotItTooltipShowcase() {
    var gotItShowcaseBody by remember { mutableStateOf(buildGotItBody {}) }

    var showImage by remember { mutableStateOf(false) }
    var showImageBorder by remember { mutableStateOf(false) }

    var showIconOrStep by remember { mutableStateOf(false) }
    var isIconMode by remember { mutableStateOf(false) }
    var chosenIconOption by remember { mutableStateOf(IconOption.entries.first()) }
    val stepNumberState = rememberTextFieldState("1")
    val stepValue by remember { derivedStateOf { stepNumberState.textAsString.toIntOrNull()?.coerceIn(1, 99) ?: 1 } }
    val iconOrStep by
        remember(isIconMode, chosenIconOption, stepValue) {
            derivedStateOf {
                if (isIconMode) {
                    GotItIconOrStep.Icon { Icon(chosenIconOption.icon, chosenIconOption.contentDescription) }
                } else {
                    GotItIconOrStep.Step(stepValue)
                }
            }
        }

    var showHeader by remember { mutableStateOf(false) }
    val headerText = rememberTextFieldState("This is the header text")

    var setMaxWidth by remember { mutableStateOf(false) }
    val maxWidthValue = rememberTextFieldState("200")

    val possibleAnchors =
        mapOf(
            "Start" to Alignment.CenterStart,
            "Top" to Alignment.TopCenter,
            "End" to Alignment.CenterEnd,
            "Bottom" to Alignment.BottomCenter,
            "Custom" to Alignment.Center,
        )
    var currentSelectedAnchor by remember { mutableStateOf(possibleAnchors.entries.first()) }
    var horizontalBias by remember { mutableFloatStateOf(0f) }
    var verticalBias by remember { mutableFloatStateOf(0f) }
    val effectiveAnchor by remember {
        derivedStateOf {
            if (currentSelectedAnchor.key == "Custom") {
                BiasAlignment(horizontalBias, verticalBias)
            } else {
                currentSelectedAnchor.value
            }
        }
    }
    val possibleGotItBalloonPositions =
        mapOf(
            "Start" to GotItBalloonPosition.START,
            "Top" to GotItBalloonPosition.ABOVE,
            "End" to GotItBalloonPosition.END,
            "Bottom" to GotItBalloonPosition.BELOW,
        )
    var currentSelectedBalloonPosition by remember { mutableStateOf(possibleGotItBalloonPositions.entries.first()) }
    val componentOffset = rememberTextFieldState("0")
    val offsetValueDp by remember { derivedStateOf { (componentOffset.textAsString.toIntOrNull() ?: 0).dp } }

    var showLink by remember { mutableStateOf(false) }
    var isExternalLink by remember { mutableStateOf(false) }
    val linkLabel = rememberTextFieldState("This is a link")
    val linkPrint = rememberTextFieldState("You just clicked the link!")
    val externalLinkUri = rememberTextFieldState(JEWEL_README)
    val linkType by remember {
        derivedStateOf {
            if (isExternalLink) {
                GotItLink.Browser(linkLabel.textAsString, externalLinkUri.textAsString) {
                    println(linkPrint.textAsString)
                }
            } else {
                GotItLink.Regular(linkLabel.textAsString) { println(linkPrint.textAsString) }
            }
        }
    }

    var addTimeout by remember { mutableStateOf(false) }
    val timeoutSecondsState = rememberTextFieldState("5")
    var timeoutUnit by remember { mutableStateOf(TimeoutUnit.SECONDS) }
    val timeoutDuration by remember {
        derivedStateOf {
            val n = timeoutSecondsState.textAsString.toIntOrNull() ?: 5
            if (timeoutUnit == TimeoutUnit.MILLISECONDS) {
                n.milliseconds
            } else if (timeoutUnit == TimeoutUnit.SECONDS) {
                n.seconds
            } else {
                n.minutes
            }
        }
    }

    val onShownText = rememberTextFieldState("The component was just shown!")

    var setEscapePressed by remember { mutableStateOf(false) }
    val onEscapePressedText = rememberTextFieldState("You just pressed Escape key")

    var showPrimaryButton by remember { mutableStateOf(true) }
    var usePrimaryDefault by remember { mutableStateOf(true) }
    val primaryLabelState = rememberTextFieldState("Got it")
    val primaryPrintState = rememberTextFieldState("Primary button clicked!")
    var showSecondaryButton by remember { mutableStateOf(false) }
    val secondaryLabelState = rememberTextFieldState("Skip All")
    val secondaryPrintState = rememberTextFieldState("Secondary button clicked!")
    val buttons by remember {
        derivedStateOf {
            val primary =
                when {
                    !showPrimaryButton -> null
                    usePrimaryDefault -> GotItButton.Default
                    else -> GotItButton(primaryLabelState.textAsString) { println(primaryPrintState.textAsString) }
                }
            val secondary =
                if (showSecondaryButton) {
                    GotItButton(secondaryLabelState.textAsString) { println(secondaryPrintState.textAsString) }
                } else {
                    null
                }
            GotItButtons(primary = primary, secondary = secondary)
        }
    }

    var isVisible by remember { mutableStateOf(false) }

    VerticallyScrollableContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(end = scrollbarContentSafePadding(), bottom = 2.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GotItTooltip(
                    body = gotItShowcaseBody,
                    visible = isVisible,
                    onDismiss = { isVisible = false },
                    header = if (showHeader) headerText.textAsString else null,
                    iconOrStep = if (showIconOrStep) iconOrStep else null,
                    buttons = buttons,
                    link = if (showLink) linkType else null,
                    image = if (showImage) GotItImage("drawables/cool_jewel.png", null, showImageBorder) else null,
                    maxWidth = if (setMaxWidth) maxWidthValue.textAsString.toIntOrNull()?.dp else null,
                    timeout = if (addTimeout) timeoutDuration else Duration.INFINITE,
                    gotItBalloonPosition = currentSelectedBalloonPosition.value,
                    anchor = effectiveAnchor,
                    onShow = { println(onShownText.textAsString) },
                    onEscapePress = if (setEscapePressed) ({ println(onEscapePressedText.textAsString) }) else null,
                    offset = offsetValueDp,
                ) {
                    DefaultButton(
                        modifier =
                            Modifier.onKeyEvent { keyEvent ->
                                when (keyEvent.key) {
                                    Key.J if keyEvent.type == KeyEventType.KeyDown -> {
                                        val entries = possibleGotItBalloonPositions.entries.toList()
                                        val idx = entries.indexOf(currentSelectedBalloonPosition)
                                        currentSelectedBalloonPosition = entries[(idx + 1) % entries.size]
                                        true
                                    }

                                    Key.H if keyEvent.type == KeyEventType.KeyDown -> {
                                        val entries = possibleAnchors.entries.filter { it.key != "Custom" }.toList()
                                        val idx = entries.indexOf(currentSelectedAnchor).coerceAtLeast(0)
                                        currentSelectedAnchor = entries[(idx + 1) % entries.size]
                                        true
                                    }

                                    else -> false
                                }
                            },
                        onClick = { isVisible = !isVisible },
                    ) {
                        Text("Show GotIt Component")
                    }
                }
            }

            Text("Playground", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            GroupHeader("Placement", Modifier.padding(vertical = 12.dp))
            Column(modifier = Modifier.padding(start = 8.dp)) {
                AnchorAndPositionSection(
                    anchors = possibleAnchors,
                    currentAnchor = currentSelectedAnchor,
                    onAnchorChange = { currentSelectedAnchor = it },
                    horizontalBias = horizontalBias,
                    onHorizontalBiasChange = { horizontalBias = it },
                    verticalBias = verticalBias,
                    onVerticalBiasChange = { verticalBias = it },
                    positions = possibleGotItBalloonPositions,
                    currentPosition = currentSelectedBalloonPosition,
                    onPositionChange = { currentSelectedBalloonPosition = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Balloon offset: ")
                    TextField(
                        modifier = Modifier.padding(start = 4.dp),
                        state = componentOffset,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        inputTransformation = remember { intInputTransformation() },
                    )
                }
            }

            GroupHeader("Appearance", Modifier.padding(vertical = 12.dp))

            Column(modifier = Modifier.padding(start = 8.dp)) {
                GroupHeader("Body")
                TextControls(gotItText = { type -> gotItShowcaseBody = type })

                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CheckboxRow(text = "Show image", checked = showImage, onCheckedChange = { showImage = it })

                    AnimatedVisibility(showImage) {
                        CheckboxRow(
                            text = "Show border",
                            checked = showImageBorder,
                            onCheckedChange = { showImageBorder = it },
                        )
                    }
                }

                ShowHeaderRow(
                    showHeader = showHeader,
                    onShowHeaderChange = { showHeader = it },
                    headerText = headerText,
                )

                ShowIconOrStepSection(
                    showIconOrStep = showIconOrStep,
                    onShowChange = { showIconOrStep = it },
                    isIconMode = isIconMode,
                    onModeChange = { isIconMode = it },
                    stepNumberState = stepNumberState,
                    chosenIcon = chosenIconOption,
                    onIconChange = { chosenIconOption = it },
                )

                SetMaxWidthRow(
                    setMaxWidth = setMaxWidth,
                    onSetChange = { setMaxWidth = it },
                    maxWidthValue = maxWidthValue,
                )

                GroupHeader("Behavior", Modifier.padding(vertical = 12.dp))
                LinkSection(
                    showLink = showLink,
                    onShowChange = { showLink = it },
                    isExternalLink = isExternalLink,
                    onTypeChange = { isExternalLink = it },
                    linkLabel = linkLabel,
                    linkPrint = linkPrint,
                    externalLinkUri = externalLinkUri,
                )

                TimeoutSection(
                    addTimeout = addTimeout,
                    onAddChange = { addTimeout = it },
                    timeoutSecondsState = timeoutSecondsState,
                    timeoutUnit = timeoutUnit,
                    onUnitChange = { timeoutUnit = it },
                )

                EscapePressedSection(
                    setEscapePressed = setEscapePressed,
                    onSetChange = { setEscapePressed = it },
                    onEscapePressedText = onEscapePressedText,
                )

                Row(modifier = Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("onShown text: ")
                    TextField(state = onShownText, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                }

                GroupHeader("Buttons", Modifier.padding(vertical = 12.dp))
                ButtonsSection(
                    showPrimaryButton = showPrimaryButton,
                    onShowPrimaryChange = { showPrimaryButton = it },
                    usePrimaryDefault = usePrimaryDefault,
                    onUsePrimaryDefaultChange = { usePrimaryDefault = it },
                    primaryLabelState = primaryLabelState,
                    primaryPrintState = primaryPrintState,
                    showSecondaryButton = showSecondaryButton,
                    onShowSecondaryChange = { showSecondaryButton = it },
                    secondaryLabelState = secondaryLabelState,
                    secondaryPrintState = secondaryPrintState,
                )
            }
        }
    }
}

@Composable
private fun ShowHeaderRow(showHeader: Boolean, onShowHeaderChange: (Boolean) -> Unit, headerText: TextFieldState) {
    Row(modifier = Modifier.padding(bottom = 4.dp)) {
        CheckboxRow(text = "Show header: ", checked = showHeader, onCheckedChange = onShowHeaderChange)

        AnimatedVisibility(showHeader) {
            TextField(
                modifier = Modifier.padding(start = 4.dp),
                state = headerText,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
        }
    }
}

@Composable
private fun ShowIconOrStepSection(
    showIconOrStep: Boolean,
    onShowChange: (Boolean) -> Unit,
    isIconMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    stepNumberState: TextFieldState,
    chosenIcon: IconOption,
    onIconChange: (IconOption) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        CheckboxRow(text = "Show Icon or Step", checked = showIconOrStep, onCheckedChange = onShowChange)

        AnimatedVisibility(showIconOrStep) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButtonRow(text = "Step", selected = !isIconMode, onClick = { onModeChange(false) })
                    RadioButtonRow(text = "Icon", selected = isIconMode, onClick = { onModeChange(true) })
                }

                AnimatedVisibility(!isIconMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Step number (between 1 and 99): ")
                        TextField(
                            modifier = Modifier.padding(start = 4.dp),
                            state = stepNumberState,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            inputTransformation = remember { intInputTransformation(1, 99) },
                        )
                    }
                }

                AnimatedVisibility(isIconMode) {
                    Row {
                        IconOption.entries.forEach { option ->
                            RadioButtonRow(
                                text = option.type,
                                selected = option == chosenIcon,
                                onClick = { onIconChange(option) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetMaxWidthRow(setMaxWidth: Boolean, onSetChange: (Boolean) -> Unit, maxWidthValue: TextFieldState) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Row {
            CheckboxRow(text = "Set max width: ", checked = setMaxWidth, onCheckedChange = onSetChange)

            AnimatedVisibility(setMaxWidth) {
                Column {
                    TextField(
                        modifier = Modifier.padding(start = 4.dp),
                        state = maxWidthValue,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        inputTransformation = remember { intInputTransformation() },
                    )
                }
            }
        }
        AnimatedVisibility(setMaxWidth) {
            Column { InfoText("Note: this will only take effect if you don't show an image.") }
        }
    }
}

@Suppress("MultipleEmitters")
@Composable
private fun AnchorAndPositionSection(
    anchors: Map<String, Alignment>,
    currentAnchor: Map.Entry<String, Alignment>,
    onAnchorChange: (Map.Entry<String, Alignment>) -> Unit,
    horizontalBias: Float,
    onHorizontalBiasChange: (Float) -> Unit,
    verticalBias: Float,
    onVerticalBiasChange: (Float) -> Unit,
    positions: Map<String, GotItBalloonPosition>,
    currentPosition: Map.Entry<String, GotItBalloonPosition>,
    onPositionChange: (Map.Entry<String, GotItBalloonPosition>) -> Unit,
) {
    Row(modifier = Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Component Anchor: ")

        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            anchors.forEach { anchor ->
                RadioButtonRow(
                    text = anchor.key,
                    selected = currentAnchor == anchor,
                    onClick = { onAnchorChange(anchor) },
                )
            }
        }
    }

    AnimatedVisibility(currentAnchor.key == "Custom") {
        Column(modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)) {
            // Labels are kept in separate rows from sliders so the dynamic text content
            // doesn't change the slider's measured width, which would recreate draggableState
            // (keyed on maxPx) and cancel the active drag gesture on every value update.
            Text("Horizontal bias: ${horizontalBias.toDisplayString()}")
            Slider(
                value = horizontalBias,
                onValueChange = onHorizontalBiasChange,
                modifier = Modifier.fillMaxWidth(),
                valueRange = -1f..1f,
            )
            Text("Vertical bias: ${verticalBias.toDisplayString()}")
            Slider(
                value = verticalBias,
                onValueChange = onVerticalBiasChange,
                modifier = Modifier.fillMaxWidth(),
                valueRange = -1f..1f,
            )
        }
    }

    Row(modifier = Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Balloon Position: ")

        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            positions.forEach { position ->
                RadioButtonRow(
                    text = position.key,
                    selected = currentPosition == position,
                    onClick = { onPositionChange(position) },
                )
            }
        }
    }
}

private fun Float.toDisplayString() = "%.2f".format(this)

@Composable
private fun LinkSection(
    showLink: Boolean,
    onShowChange: (Boolean) -> Unit,
    isExternalLink: Boolean,
    onTypeChange: (Boolean) -> Unit,
    linkLabel: TextFieldState,
    linkPrint: TextFieldState,
    externalLinkUri: TextFieldState,
) {
    CheckboxRow(
        modifier = Modifier.padding(top = 4.dp),
        text = "Add a Link:",
        checked = showLink,
        onCheckedChange = onShowChange,
    )

    AnimatedVisibility(showLink) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButtonRow(text = "Regular Link", selected = !isExternalLink, onClick = { onTypeChange(false) })
                RadioButtonRow(text = "External Link", selected = isExternalLink, onClick = { onTypeChange(true) })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Link Label: ")
                TextField(
                    modifier = Modifier.padding(vertical = 4.dp),
                    state = linkLabel,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )

                Text("Link Action Text: ")
                TextField(
                    modifier = Modifier.padding(start = 8.dp),
                    state = linkPrint,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )

                AnimatedVisibility(isExternalLink) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Link Browser URI: ")
                        TextField(
                            modifier = Modifier.padding(start = 4.dp),
                            state = externalLinkUri,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeoutSection(
    addTimeout: Boolean,
    onAddChange: (Boolean) -> Unit,
    timeoutSecondsState: TextFieldState,
    timeoutUnit: TimeoutUnit,
    onUnitChange: (TimeoutUnit) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        CheckboxRow(text = "Set a timeout", checked = addTimeout, onCheckedChange = onAddChange)

        AnimatedVisibility(addTimeout) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                TextField(
                    state = timeoutSecondsState,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    inputTransformation = remember { intInputTransformation() },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RadioButtonRow(
                        text = "Milliseconds",
                        selected = timeoutUnit == TimeoutUnit.MILLISECONDS,
                        onClick = { onUnitChange(TimeoutUnit.MILLISECONDS) },
                    )
                    RadioButtonRow(
                        text = "Seconds",
                        selected = timeoutUnit == TimeoutUnit.SECONDS,
                        onClick = { onUnitChange(TimeoutUnit.SECONDS) },
                    )
                    RadioButtonRow(
                        text = "Minutes",
                        selected = timeoutUnit == TimeoutUnit.MINUTES,
                        onClick = { onUnitChange(TimeoutUnit.MINUTES) },
                    )
                }
            }
        }

        AnimatedVisibility(addTimeout) {
            InfoText("Note: by setting a timeout, the primary and secondary buttons won't appear.")
        }
    }
}

@Composable
private fun EscapePressedSection(
    setEscapePressed: Boolean,
    onSetChange: (Boolean) -> Unit,
    onEscapePressedText: TextFieldState,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row {
            CheckboxRow(text = "Set onEscapePressed: ", checked = setEscapePressed, onCheckedChange = onSetChange)

            AnimatedVisibility(setEscapePressed) {
                TextField(
                    modifier = Modifier.padding(start = 4.dp),
                    state = onEscapePressedText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
        }

        AnimatedVisibility(setEscapePressed) {
            InfoText(
                "Note: by setting this parameter, users can press Esc and the popup will be dismissed. " +
                    "This only works if the popup is currently focused."
            )
        }
    }
}

@Suppress("MultipleEmitters")
@Composable
private fun ButtonsSection(
    showPrimaryButton: Boolean,
    onShowPrimaryChange: (Boolean) -> Unit,
    usePrimaryDefault: Boolean,
    onUsePrimaryDefaultChange: (Boolean) -> Unit,
    primaryLabelState: TextFieldState,
    primaryPrintState: TextFieldState,
    showSecondaryButton: Boolean,
    onShowSecondaryChange: (Boolean) -> Unit,
    secondaryLabelState: TextFieldState,
    secondaryPrintState: TextFieldState,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        CheckboxRow(text = "Show primary button", checked = showPrimaryButton, onCheckedChange = onShowPrimaryChange)

        AnimatedVisibility(showPrimaryButton) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButtonRow(
                        text = "Default (\"Got it\")",
                        selected = usePrimaryDefault,
                        onClick = { onUsePrimaryDefaultChange(true) },
                    )
                    RadioButtonRow(
                        text = "Custom",
                        selected = !usePrimaryDefault,
                        onClick = { onUsePrimaryDefaultChange(false) },
                    )
                }

                AnimatedVisibility(!usePrimaryDefault) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Label: ")
                        TextField(
                            modifier = Modifier.padding(vertical = 4.dp),
                            state = primaryLabelState,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        )
                        Text("Action text: ")
                        TextField(
                            modifier = Modifier.padding(start = 4.dp),
                            state = primaryPrintState,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        )
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        CheckboxRow(
            text = "Show secondary button",
            checked = showSecondaryButton,
            onCheckedChange = onShowSecondaryChange,
        )

        AnimatedVisibility(showSecondaryButton) {
            Row(modifier = Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Label: ")
                TextField(
                    modifier = Modifier.padding(vertical = 4.dp),
                    state = secondaryLabelState,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Text("Action text: ")
                TextField(
                    modifier = Modifier.padding(start = 4.dp),
                    state = secondaryPrintState,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
        }
    }
}

@Composable
private fun TextControls(gotItText: (GotItBody) -> Unit) {
    val currentGotItText by rememberUpdatedState(gotItText)
    var isRichBody by remember { mutableStateOf(false) }

    val simpleTextState =
        rememberTextFieldState(
            "Hi, this is a simple text :) Pro tip: while the button is focused, you can press J to change " +
                "the balloon position and H to change the anchor"
        )
    var richBodyState by remember { mutableStateOf(buildGotItBody {}) }

    val body by
        remember(isRichBody, simpleTextState.text, richBodyState) {
            derivedStateOf {
                if (isRichBody) richBodyState else buildGotItBody { append(simpleTextState.textAsString) }
            }
        }

    LaunchedEffect(body) { currentGotItText(body) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RadioButtonRow(text = "Simple Text", selected = !isRichBody, onClick = { isRichBody = false })
            RadioButtonRow(text = "Rich Text", selected = isRichBody, onClick = { isRichBody = true })
        }

        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            if (!isRichBody) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    state = simpleTextState,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            } else {
                RichBodyControls(richBodyChanged = { updatedBody -> richBodyState = updatedBody })
            }
        }
    }
}

@Composable
private fun RichBodyControls(richBodyChanged: (GotItBody) -> Unit) {
    val currentRichBodyChanged by rememberUpdatedState(richBodyChanged)
    val richBodyText = rememberTextFieldState("This is an example of a rich body text.")

    var richBodyCodeChecked by remember { mutableStateOf(false) }
    val richBodyCodeText = rememberTextFieldState("./gradlew is cool")

    var richBodyIconChecked by remember { mutableStateOf(false) }
    var richBodyChosenIcon by remember { mutableStateOf(IconOption.entries.first()) }

    var richBodyBoldChecked by remember { mutableStateOf(false) }
    val richBodyBoldText = rememberTextFieldState("This is pretty important because it's bold")

    var richBodyLinkChecked by remember { mutableStateOf(false) }
    val richBodyLinkText = rememberTextFieldState("Click this link!")
    val richBodyLinkPrint = rememberTextFieldState("You just clicked the link :)")

    var richBodyBrowserLinkChecked by remember { mutableStateOf(false) }
    val richBodyExternalLinkText = rememberTextFieldState("This opens an external link")
    val richBodyExternalLinkUri = rememberTextFieldState(JEWEL_README)

    val finalRichBodyText by
        remember(
            richBodyText,
            richBodyCodeChecked,
            richBodyIconChecked,
            richBodyChosenIcon,
            richBodyBoldChecked,
            richBodyLinkChecked,
            richBodyBrowserLinkChecked,
        ) {
            derivedStateOf {
                buildGotItBody {
                    append(richBodyText.textAsString)
                    if (richBodyCodeChecked) {
                        append(" You can add small code snippets here, like: ")
                        code(richBodyCodeText.textAsString)
                        append(".")
                    }
                    if (richBodyIconChecked) {
                        append(" You can add any icon you want: ")
                        icon(richBodyChosenIcon.type) {
                            Icon(
                                key = richBodyChosenIcon.icon,
                                contentDescription = richBodyChosenIcon.contentDescription,
                            )
                        }
                        append(".")
                    }
                    if (richBodyBoldChecked) {
                        append(" You can add bold text: ")
                        bold(richBodyBoldText.textAsString)
                        append(".")
                    }
                    if (richBodyLinkChecked) {
                        append(" You can add a link: ")
                        link(richBodyLinkText.textAsString) { println(richBodyLinkPrint.text) }
                        append(".")
                    }
                    if (richBodyBrowserLinkChecked) {
                        append(" You can add external links too: ")
                        browserLink(richBodyExternalLinkText.textAsString, richBodyExternalLinkUri.textAsString)
                        append(".")
                    }
                }
            }
        }

    LaunchedEffect(finalRichBodyText) { currentRichBodyChanged(finalRichBodyText) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth(),
            state = richBodyText,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )

        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            CheckboxRow(
                text = "Add code: ",
                checked = richBodyCodeChecked,
                onCheckedChange = { richBodyCodeChecked = it },
            )

            AnimatedVisibility(richBodyCodeChecked) {
                TextField(
                    modifier = Modifier.padding(start = 4.dp),
                    state = richBodyCodeText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
        }

        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            CheckboxRow(
                text = "Add icon: ",
                checked = richBodyIconChecked,
                onCheckedChange = { richBodyIconChecked = it },
            )

            AnimatedVisibility(richBodyIconChecked) {
                Row(
                    modifier = Modifier.padding(start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconOption.entries.forEach { option ->
                        RadioButtonRow(
                            text = option.type,
                            selected = option == richBodyChosenIcon,
                            onClick = { richBodyChosenIcon = option },
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            CheckboxRow(
                text = "Add bold text: ",
                checked = richBodyBoldChecked,
                onCheckedChange = { richBodyBoldChecked = it },
            )

            AnimatedVisibility(richBodyBoldChecked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        modifier = Modifier.padding(start = 4.dp),
                        state = richBodyBoldText,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            CheckboxRow(
                text = "Add link:",
                checked = richBodyLinkChecked,
                onCheckedChange = { richBodyLinkChecked = it },
            )

            AnimatedVisibility(richBodyLinkChecked) {
                Row(horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    Text("Link text")
                    TextField(
                        modifier = Modifier.padding(vertical = 4.dp),
                        state = richBodyLinkText,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )

                    Text("Link click text: ")
                    TextField(
                        modifier = Modifier.padding(start = 4.dp),
                        state = richBodyLinkPrint,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            CheckboxRow(
                text = "Add external link",
                checked = richBodyBrowserLinkChecked,
                onCheckedChange = { richBodyBrowserLinkChecked = it },
            )

            AnimatedVisibility(richBodyBrowserLinkChecked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("External Link Text")
                    TextField(
                        modifier = Modifier.padding(vertical = 4.dp),
                        state = richBodyExternalLinkText,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )

                    Text("External Link URI: ")
                    TextField(
                        modifier = Modifier.padding(start = 4.dp),
                        state = richBodyExternalLinkUri,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
            }
        }
    }
}

private enum class IconOption(val type: String, val icon: IntelliJIconKey, val contentDescription: String) {
    INFORMATION("Balloon Information", AllIconsKeys.General.BalloonInformation, "Balloon Info"),
    RESUME("Resume", AllIconsKeys.Actions.Resume, "Resume icon"),
    ARROW_UP("Arrow Up", AllIconsKeys.General.ArrowUp, "Arrow up icon"),
    KOTLIN("Kotlin", AllIconsKeys.Language.Kotlin, "Kotlin icon"),
}

private fun intInputTransformation(min: Int = 0, max: Int = Int.MAX_VALUE) =
    InputTransformation.byValue { current, proposed ->
        val text = proposed.toString()
        if (text.isEmpty()) return@byValue proposed

        val intValue = text.toIntOrNull()
        if (intValue == null || intValue !in min..max) {
            current
        } else {
            proposed
        }
    }

private val TextFieldState.textAsString
    get() = this.text.toString()
