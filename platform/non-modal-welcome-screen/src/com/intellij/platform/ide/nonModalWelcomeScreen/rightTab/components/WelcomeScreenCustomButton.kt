package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.ButtonState
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.theme.defaultButtonStyle

@Composable
@ApiStatus.Internal
fun WelcomeScreenCustomButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  style: ButtonStyle = JewelTheme.defaultButtonStyle,
  textStyle: TextStyle = JewelTheme.defaultTextStyle,
  content: @Composable () -> Unit,
) {
  ButtonImpl(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    forceFocused = false,
    onStateChange = {},
    interactionSource = interactionSource,
    style = style,
    textStyle = textStyle,
    content = content,
  )
}

/**
 * Main differences with [org/jetbrains/jewel/ui/component/Button.kt]:
 * - Uses customBackgroundFor method
 * - Has no border
 */
@Composable
private fun ButtonImpl(
  onClick: () -> Unit,
  modifier: Modifier,
  enabled: Boolean,
  forceFocused: Boolean,
  onStateChange: (ButtonState) -> Unit,
  interactionSource: MutableInteractionSource,
  style: ButtonStyle,
  textStyle: TextStyle,
  content: @Composable () -> Unit,
  secondaryContent: @Composable (() -> Unit)? = null,
) {
  var buttonState by
  remember(interactionSource) { mutableStateOf(ButtonState.of(enabled = enabled, focused = forceFocused)) }

  remember(enabled) { buttonState = buttonState.copy(enabled = enabled) }
  // This helps with managing and keeping the button focus state in sync
  // when the Composable is used for the SplitButton variant.
  // This variant is the only one that overrides the default focus state
  // according to the popup visibility.
  var actuallyFocused by remember { mutableStateOf(false) }
  remember(forceFocused) { buttonState = buttonState.copy(focused = if (forceFocused) true else actuallyFocused) }

  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { interaction ->
      buttonState =
        when (interaction) {
          is PressInteraction.Press -> buttonState.copy(pressed = true)
          is PressInteraction.Cancel,
          is PressInteraction.Release -> buttonState.copy(pressed = false)

          is HoverInteraction.Enter -> buttonState.copy(hovered = true)
          is HoverInteraction.Exit -> buttonState.copy(hovered = false)
          is FocusInteraction.Focus -> {
            actuallyFocused = true
            buttonState.copy(focused = true)
          }

          is FocusInteraction.Unfocus -> {
            actuallyFocused = false
            buttonState.copy(focused = forceFocused)
          }

          else -> buttonState
        }
      onStateChange(buttonState)
    }
  }

  val shape = RoundedCornerShape(style.metrics.cornerSize)
  val colors = style.colors

  Box(
    modifier =
      modifier
        .clickable(
          onClick = onClick,
          enabled = enabled,
          role = Role.Button,
          interactionSource = interactionSource,
          indication = null,
        )
        .background(colors.customBackgroundFor(buttonState).value, shape)
        .focusOutline(
          showOutline = buttonState.isFocused || buttonState.isPressed,
          outlineShape = shape,
          alignment = style.focusOutlineAlignment,
          expand = style.metrics.focusOutlineExpand,
        ),
    propagateMinConstraints = true,
  ) {
    val contentColor by colors.contentFor(buttonState)

    CompositionLocalProvider(
      LocalContentColor provides contentColor.takeOrElse { textStyle.color },
      LocalTextStyle provides textStyle.copy(color = contentColor.takeOrElse { textStyle.color }),
    ) {
      Row(
        Modifier.defaultMinSize(style.metrics.minSize.width).height(style.metrics.minSize.height),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.padding(style.metrics.padding)) { content() }
        secondaryContent?.invoke()
      }
    }
  }
}

/**
 * Correctly handles hovered state without focus
 * See [org.jetbrains.jewel.ui.component.styling.ButtonColors.backgroundFor]
 */
@Composable
private fun ButtonColors.customBackgroundFor(state: ButtonState): State<Brush> =
  rememberUpdatedState(
    with(state) {
      when {
        isPressed -> backgroundPressed
        isHovered -> backgroundHovered
        isFocused -> backgroundFocused
        isActive -> background
        else -> background
      }
    }
  )
