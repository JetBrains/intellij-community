// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.showcase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*

@Composable
internal fun ComposeShowcase() {
  Column(
    verticalArrangement = Arrangement.spacedBy(15.dp),
    modifier = Modifier.padding(10.dp)
  ) {
    Title()
    Row {
      val scrollState = rememberScrollState()
      Column(
        verticalArrangement = Arrangement.spacedBy(15.dp),
        modifier = Modifier.weight(1f).verticalScroll(scrollState)
      ) {
        CheckBox()
        RadioButton()
        Buttons()
        Label()
        SelectableText()
        Tabs()
        LinkLabels()
        TextField()
      }

      val adapter = rememberScrollbarAdapter(scrollState)
      androidx.compose.foundation.VerticalScrollbar(adapter)
    }
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
    horizontalArrangement = Arrangement.spacedBy(20.dp)
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
        label = "Tab $id",
        closable = false,
        onClick = { selectedTabIndex = index },
      )
    }
  }

  TabStrip(tabs)
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
private fun TextField() {
  var textFieldState by remember { mutableStateOf("") }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("Text field:")
    TextField(
      textFieldState,
      onValueChange = {
        textFieldState = it
      },
      modifier = Modifier.padding(5.dp)
    )
  }
}
