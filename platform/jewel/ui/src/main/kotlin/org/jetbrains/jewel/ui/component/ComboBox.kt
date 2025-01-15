package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.collect
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.util.thenIf

@Composable
public fun ComboBox(
    labelText: String,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onArrowDownPress: () -> Unit = {},
    onArrowUpPress: () -> Unit = {},
    popupManager: PopupManager = PopupManager(),
    popupContent: @Composable () -> Unit,
) {
    var chevronHovered by remember { mutableStateOf(false) }

    val popupVisible by popupManager.isPopupVisible

    var comboBoxState by remember { mutableStateOf(ComboBoxState.of(enabled = isEnabled)) }
    val comboBoxFocusRequester = remember { FocusRequester() }

    remember(isEnabled) { comboBoxState = comboBoxState.copy(enabled = isEnabled) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> comboBoxState = comboBoxState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> comboBoxState = comboBoxState.copy(pressed = false)
                is HoverInteraction.Enter -> comboBoxState = comboBoxState.copy(hovered = true)
                is HoverInteraction.Exit -> comboBoxState = comboBoxState.copy(hovered = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val borderColor by style.colors.borderFor(comboBoxState)
    var comboBoxWidth by remember { mutableStateOf(Dp.Unspecified) }
    val density = LocalDensity.current

    Box(
        modifier =
            modifier
                .focusRequester(comboBoxFocusRequester)
                .onFocusChanged { focusState ->
                    comboBoxState = comboBoxState.copy(focused = focusState.isFocused)
                    if (!focusState.isFocused) {
                        popupManager.setPopupVisible(false)
                    }
                }
                .thenIf(isEnabled) {
                    focusable(true, interactionSource)
                        .onHover { chevronHovered = it }
                        .pointerInput(interactionSource) {
                            detectPressAndCancel(
                                onPress = {
                                    popupManager.setPopupVisible(!popupVisible)
                                    comboBoxFocusRequester.requestFocus()
                                },
                                onCancel = { popupManager.setPopupVisible(false) },
                            )
                        }
                        .semantics(mergeDescendants = true) { role = Role.DropdownList }
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            when {
                                it.key == Key.Spacebar -> {
                                    popupManager.setPopupVisible(!popupVisible)
                                    true
                                }

                                it.key == Key.DirectionDown -> {
                                    if (popupVisible) {
                                        onArrowDownPress()
                                    } else {
                                        popupManager.setPopupVisible(true)
                                    }
                                    true
                                }

                                it.key == Key.DirectionUp && popupVisible -> {
                                    onArrowUpPress()
                                    true
                                }

                                it.key == Key.Escape && popupVisible -> {
                                    popupManager.setPopupVisible(false)
                                    true
                                }

                                else -> false
                            }
                        }
                }
                .background(style.colors.backgroundFor(comboBoxState, false).value, shape)
                .thenIf(outline == Outline.None) {
                    focusOutline(state = comboBoxState, outlineShape = shape, alignment = Stroke.Alignment.Center)
                        .border(
                            alignment = Stroke.Alignment.Inside,
                            width = style.metrics.borderWidth,
                            color = borderColor,
                            shape = shape,
                        )
                }
                .outline(
                    state = comboBoxState,
                    outline = outline,
                    outlineShape = shape,
                    alignment = Stroke.Alignment.Center,
                )
                .widthIn(min = style.metrics.minSize.width)
                .height(style.metrics.minSize.height)
                .onSizeChanged { comboBoxWidth = with(density) { it.width.toDp() } },
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides style.colors.contentFor(comboBoxState).value) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusable(false).focusProperties { canFocus = false },
            ) {
                val textColor = if (isEnabled) Color.Unspecified else style.colors.borderDisabled
                Text(
                    text = labelText,
                    style = textStyle.copy(color = textColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.testTag("Jewel.ComboBox.NonEditableText")
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(style.metrics.contentPadding),
                )

                Chevron(style, isEnabled)
            }
        }

        if (popupVisible) {
            val maxHeight =
                if (maxPopupHeight == Dp.Unspecified) {
                    JewelTheme.comboBoxStyle.metrics.maxPopupHeight
                } else {
                    maxPopupHeight
                }

            PopupContainer(
                onDismissRequest = {
                    if (!chevronHovered) {
                        popupManager.setPopupVisible(false)
                    }
                },
                modifier =
                    popupModifier
                        .testTag("Jewel.ComboBox.Popup")
                        .heightIn(max = maxHeight)
                        .width(comboBoxWidth)
                        .onClick { popupManager.setPopupVisible(false) },
                horizontalAlignment = Alignment.Start,
                popupProperties = PopupProperties(focusable = false),
                content = popupContent,
            )
        }
    }
}

@Composable
private fun Chevron(style: ComboBoxStyle, isEnabled: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.testTag("Jewel.ComboBox.ChevronContainer").size(style.metrics.arrowAreaSize),
    ) {
        val iconColor = if (isEnabled) Color.Unspecified else style.colors.borderDisabled
        Icon(key = style.icons.chevronDown, tint = iconColor, contentDescription = null)
    }
}
