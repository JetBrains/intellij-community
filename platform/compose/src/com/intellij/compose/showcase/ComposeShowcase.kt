// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.*

@Composable
internal fun ComposeShowcase() {
  Column(
    verticalArrangement = Arrangement.spacedBy(15.dp),
    modifier = Modifier.padding(10.dp)
  ) {
    CheckBox()
    RadioButton()
    Button()
    Label()
    Tabs()
    LinkLabels()
    TextField()
  }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CheckBox() {
  var checkedState by remember { mutableStateOf(false) }
  Row(
    horizontalArrangement = Arrangement.spacedBy(5.dp)
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
    horizontalArrangement = Arrangement.spacedBy(5.dp)
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
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Text("label:")
    Text("Some label")
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
    horizontalArrangement = Arrangement.spacedBy(5.dp)
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
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Text("Text field:")
    TextField(textFieldState, onValueChange = {
      textFieldState = it
    })
  }
}