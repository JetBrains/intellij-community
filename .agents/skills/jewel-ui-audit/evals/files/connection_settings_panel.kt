/*
 * A connection settings form for a Jewel-based IntelliJ plugin. The form has field-level errors
 * after fields are edited, but the initial invalid state is communicated only by a disabled button
 * tooltip. Reviewers: focus on input validation and how errors are surfaced to the user.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip

@Composable
fun ConnectionSettingsPanel(onConnect: (String, Int) -> Unit, modifier: Modifier = Modifier) {
  var host by remember { mutableStateOf("") }
  var port by remember { mutableStateOf("") }
  var hostEdited by remember { mutableStateOf(false) }
  var portEdited by remember { mutableStateOf(false) }

  val portNumber = port.toIntOrNull()
  val isHostValid = host.isNotBlank()
  val isPortValid = portNumber != null && portNumber in 1..65535
  val isValid = isHostValid && isPortValid

  val hostError = if (hostEdited && !isHostValid) "Hostname cannot be empty." else null
  val portError =
    if (portEdited && !isPortValid) {
      when {
        port.isBlank() -> "Port cannot be empty."
        portNumber == null -> "Port must be a valid number."
        else -> "Port must be between 1 and 65535."
      }
    } else {
      null
    }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      TextField(
        value = host,
        onValueChange = {
          host = it
          hostEdited = true
        },
        placeholder = { Text("Hostname") },
        modifier = Modifier.fillMaxWidth(),
        outline = if (hostError != null) Outline.Error else Outline.None,
      )
      if (hostError != null) {
        Text(
          text = hostError,
          style = JewelTheme.defaultTextStyle.copy(color = JewelTheme.globalColors.texts.error),
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      TextField(
        value = port,
        onValueChange = {
          port = it
          portEdited = true
        },
        placeholder = { Text("Port") },
        modifier = Modifier.fillMaxWidth(),
        outline = if (portError != null) Outline.Error else Outline.None,
      )
      if (portError != null) {
        Text(
          text = portError,
          style = JewelTheme.defaultTextStyle.copy(color = JewelTheme.globalColors.texts.error),
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
    }

    val tooltipText = when {
      !isHostValid -> "Please enter a valid hostname."
      !isPortValid -> "Please enter a valid port number (1-65535)."
      else -> "Connect to the specified host."
    }

    Tooltip(tooltip = { Text(tooltipText) }) {
      DefaultButton(onClick = { onConnect(host, portNumber!!) }, enabled = isValid, modifier = Modifier.fillMaxWidth()) {
        Text("Connect")
      }
    }
  }
}
