// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.showcase

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.*

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
        Button()
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

@OptIn(ExperimentalComposeUiApi::class)
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
      LocalResourceLoader.current,
      onCheckedChange = {
        checkedState = it
      }
    )
  }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RadioButton() {
  var selectedRadioButton by remember { mutableStateOf(1) }
  val resourceLoader = LocalResourceLoader.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text("radioButton")
    RadioButtonRow(
      "Value 1",
      selected = selectedRadioButton == 0,
      resourceLoader,
      onClick = {
        selectedRadioButton = 0
      }
    )
    RadioButtonRow(
      "Value 2",
      selected = selectedRadioButton == 1,
      resourceLoader,
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
private fun Button() {
  OutlinedButton(onClick = {
    // no nothing
  }) {
    Text("button")
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LinkLabels() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    val resourceLoader = LocalResourceLoader.current

    Text("Labels:")
    Link("Link", resourceLoader, onClick = {
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
    TextField(textFieldState, onValueChange = {
      textFieldState = it
    })
  }
}
