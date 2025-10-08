package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.ComboBoxState
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.detectPressAndCancel
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.comboBoxStyle

/**
 * A customized version of org/jetbrains/jewel/ui/component/ComboBox.kt
 * Requires an [iconKey] parameter to display an icon alongside the text
 * Uses a more minimalist visual styling (no background, borders, or outlines by default)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
internal fun WelcomeScreenCustomComboBox(
  iconKey: IconKey,
  labelText: String,
  modifier: Modifier = Modifier,
  popupModifier: Modifier = Modifier,
  isEnabled: Boolean = true,
  maxPopupHeight: Dp = Dp.Unspecified,
  minPopupWidth: Dp = Dp.Unspecified,
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

    var comboBoxWidth by remember { mutableStateOf(minPopupWidth) }
    val density = LocalDensity.current

  val shape = RoundedCornerShape(style.metrics.cornerSize)
    Box(
        modifier =
            modifier
                .focusRequester(comboBoxFocusRequester)
                .onFocusChanged { focusState ->
                    comboBoxState = comboBoxState.copy(focused = focusState.isFocused)
                    if (!focusState.isFocused) {
                        popupManager.setPopupVisible(false)
                        comboBoxState = comboBoxState.copy(pressed = false)
                    }
                }
                .thenIf(isEnabled) {
                    focusable(true, interactionSource)
                      .hoverable(interactionSource = interactionSource, enabled = true)
                      .background(style.colors.backgroundFor(comboBoxState, true).value, shape)
                        .focusOutline(
                          showOutline = comboBoxState.isFocused,
                          outlineShape = shape,
                        )
                        .onHover { chevronHovered = it }
                        .pointerInput(interactionSource) {
                            detectPressAndCancel(
                                onPress = {
                                    popupManager.setPopupVisible(!popupVisible)
                                    comboBoxState = comboBoxState.copy(pressed = !comboBoxState.isPressed )
                                },
                                onCancel = {
                                  popupManager.setPopupVisible(false)
                                  comboBoxState = comboBoxState.copy(pressed = false)
                                },
                            )
                        }
                        .semantics(mergeDescendants = true) { role = Role.DropdownList }
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            when {
                                it.key == Key.Spacebar -> {
                                    popupManager.setPopupVisible(!popupVisible)
                                    comboBoxState = comboBoxState.copy(pressed = false)
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
                                    comboBoxState = comboBoxState.copy(pressed = false)
                                    true
                                }

                                else -> false
                            }
                        }
                }
                .widthIn(min = style.metrics.minSize.width)
                .height(style.metrics.minSize.height)
              .onSizeChanged {
                val minWidth = comboBoxWidth.takeOrElse { 0.dp }
                comboBoxWidth = with(density) {
                  it.width.toDp().coerceAtLeast(minWidth)
                }
              },
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides style.colors.contentFor(comboBoxState).value) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusable(false).focusProperties { canFocus = false },
            ) {
              LabelIcon(style, iconKey, labelText)
                val textColor = if (isEnabled) Color.Unspecified else style.colors.borderDisabled
              Text(
                text = labelText,
                style = textStyle.copy(color = textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("Jewel.ComboBox.NonEditableText")
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
                comboBoxState = comboBoxState.copy(pressed = false)
              }
            },
            modifier =
              popupModifier
                .testTag("Jewel.ComboBox.Popup")
                .heightIn(max = maxHeight)
                .width(comboBoxWidth)
                .onClick {
                  popupManager.setPopupVisible(false)
                  comboBoxState = comboBoxState.copy(pressed = false)
                },
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

@Composable
private fun LabelIcon(style: ComboBoxStyle, iconKey: IconKey, labelText: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.testTag("Jewel.ComboBox.LabelIconContainer").size(style.metrics.arrowAreaSize),
        ) {
      Icon(key = iconKey, contentDescription = labelText)
    }
}
