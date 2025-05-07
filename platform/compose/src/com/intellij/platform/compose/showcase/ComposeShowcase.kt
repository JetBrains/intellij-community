// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.showcase

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import org.jetbrains.jewel.ui.util.isDark
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.KeyStroke

@Composable
internal fun ComposeShowcase() {
  Column(
    verticalArrangement = Arrangement.spacedBy(15.dp),
    modifier = Modifier.padding(10.dp)
  ) {
    Title()
    Text("This is Compose bundled inside IntelliJ Platform!")
    Row {
      VerticallyScrollableContainer(
        modifier = Modifier.weight(1f)
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
          CheckBox()
          RadioButton()
          Buttons()
          Label()
          SelectableText()
          Tabs()
          LinkLabels()
          TextFieldSimple()
          TextFieldWithButton()
          TooltipAreaSimple()
          InfiniteAnimation()
        }
      }
    }
  }
}

@Composable
private fun InfiniteAnimation() {
  val transition = rememberInfiniteTransition("coso")
  val animatedAlpha by
  transition.animateFloat(
    0f,
    1f,
    infiniteRepeatable(tween(durationMillis = 1000, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
  )
  Box(Modifier.alpha(animatedAlpha)) {
    Text("Animation!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun Title() {
  Text("Showcase of Jewel components", fontSize = 15.sp)
  Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CheckBox() {
  var checkedState by remember { mutableStateOf(false) }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("Checkbox:")
    CheckboxRow(
      "checkBox",
      checkedState,
      onCheckedChange = {
        checkedState = it
      }
    )
  }
}


@Composable
private fun RadioButton() {
  var selectedRadioButton by remember { mutableStateOf(1) }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("radioButton")
    RadioButtonRow(
      "Value 1",
      selected = selectedRadioButton == 0,
      onClick = {
        selectedRadioButton = 0
      }
    )
    RadioButtonRow(
      "Value 2",
      selected = selectedRadioButton == 1,
      onClick = {
        selectedRadioButton = 1
      }
    )
  }
}

@Composable
private fun Label() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Text("label:")
    Text("Some label")
  }
}

@Composable
private fun SelectableText() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    SelectionContainer {
      Text("Selectable text")
    }
  }
}

@Composable
private fun Buttons() {
  Row(
    horizontalArrangement = Arrangement.spacedBy(20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    var state1 by remember { mutableStateOf(0) }
    OutlinedButton(onClick = {
      state1++
    }) {
      Text("Click me #$state1")
    }

    var state2 by remember { mutableStateOf(0) }
    DefaultButton(onClick = {
      state2++
    }) {
      Text("Click me #$state2")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      var state3 by remember { mutableStateOf(0) }
      var focused by remember { mutableStateOf(false) }
      IconButton(
        onClick = { state3++ },
        modifier = Modifier
          .size(18.dp)
          .onFocusEvent { focused = it.isFocused }
          .background(if (focused) JBUI.CurrentTheme.Focus.focusColor().toComposeColor() else Color.Unspecified, RoundedCornerShape(4.dp))
      ) {
        Icon(AllIconsKeys.General.FitContent, contentDescription = null, iconClass = AllIcons::class.java)
      }
      Text("← Click me #$state3")
    }
  }
}

@Composable
private fun Tabs() {
  var selectedTabIndex by remember { mutableStateOf(0) }
  val tabIds by remember { mutableStateOf((1..12).toList()) }

  val tabs by derivedStateOf {
    tabIds.mapIndexed { index, id ->
      TabData.Default(
        selected = index == selectedTabIndex,
        content = {
          Text("Tab $id")
        },
        closable = false,
        onClick = { selectedTabIndex = index },
      )
    }
  }

  TabStrip(tabs, JewelTheme.defaultTabStyle)
}

@Composable
private fun LinkLabels() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("Labels:")
    Link("Link", onClick = {
      // do nothing
    })
  }
}

@Composable
private fun TextFieldSimple() {
  val textFieldState = rememberTextFieldState()
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("Text field:")
    TextField(
      textFieldState,
      modifier = Modifier.padding(5.dp)
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextFieldWithButton() {
  val textFieldState = rememberTextFieldState()
  var fileExists by remember { mutableStateOf(true) }

  LaunchedEffect(textFieldState) {
    delay(300)
    withContext(IO) {
      fileExists = textFieldState.text.isEmpty() || File(textFieldState.text.toString()).exists()
    }
  }

  fun chooseFile() {
    val descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
    descriptor.title = UIBundle.message("file.chooser.default.title")
    FileChooser.chooseFile(descriptor, null, null) {
      textFieldState.edit { replace(0, textFieldState.text.length, it.path) }
    }
  }

  val openFileChooserHint = UIBundle.message("component.with.browse.button.browse.button.tooltip.text") + " (${
    KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))
  })"

  Row(
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Choose file or folder:")
    TextField(
      textFieldState,
      modifier = Modifier
        .padding(5.dp)
        .height(28.dp)
        .onKeyEvent {
          if (it.isShiftPressed && it.key == Key.Enter) true.also { chooseFile() } else false
        },
      outline = if (fileExists) Outline.None else Outline.Error,
      placeholder = {
        Text(
          text = openFileChooserHint,
          color = JBUI.CurrentTheme.ContextHelp.FOREGROUND.toComposeColor(),
          fontSize = (JewelTheme.defaultTextStyle.fontSize.value - 2).sp,
        )
      },
      trailingIcon = {
        TooltipArea(
          tooltip = { TooltipSimple { Text(openFileChooserHint, color = JewelTheme.tooltipStyle.colors.content) } }
        ) {
          IconButton({ chooseFile() }, Modifier.size(18.dp).pointerHoverIcon(PointerIcon.Hand).focusProperties { canFocus = false }) {
            AllIcons.General.OpenDisk
            Icon(AllIconsKeys.General.OpenDisk, openFileChooserHint, iconClass = AllIcons::class.java)
          }
        }
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TooltipAreaSimple() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Hover with tooltip example:")
    TooltipArea({ TooltipSimple { Text("Sample text") } }) {

      var hovered by remember { mutableStateOf(false) }
      val cornerSize = animateDpAsState(if (hovered) 28.dp else 4.dp)

      Box(Modifier
            .onHover { hovered = it }
            .border(
              width = 2.dp,
              color = JBUI.CurrentTheme.Button.focusBorderColor(true).toComposeColor(),
              shape = RoundedCornerShape(cornerSize.value),
            )
      ) {
        Text("Hovered: $hovered", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
      }
    }
  }
}

@Composable
private fun TooltipSimple(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = modifier
      .shadow(
        elevation = JewelTheme.tooltipStyle.metrics.shadowSize,
        shape = RoundedCornerShape(JewelTheme.tooltipStyle.metrics.cornerSize),
        ambientColor = JewelTheme.tooltipStyle.colors.shadow,
        spotColor = Color.Transparent,
      )
      .background(
        color = JewelTheme.tooltipStyle.colors.background,
        shape = RoundedCornerShape(JewelTheme.tooltipStyle.metrics.cornerSize),
      )
      .border(
        width = JewelTheme.tooltipStyle.metrics.borderWidth,
        color = JewelTheme.tooltipStyle.colors.border,
        shape = RoundedCornerShape(JewelTheme.tooltipStyle.metrics.cornerSize),
      )
      .padding(JewelTheme.tooltipStyle.metrics.contentPadding),
  ) {
    OverrideDarkMode(JewelTheme.tooltipStyle.colors.background.isDark()) {
      content()
    }
  }
}
