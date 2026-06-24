/*
 * A connection settings form for a Jewel-based IntelliJ plugin. Reviewers: focus on input validation
 * and how errors are surfaced to the user.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip

@Composable
fun ConnectionSettingsPanel(onConnect: (String, Int) -> Unit, modifier: Modifier = Modifier) {
  var host by remember { mutableStateOf("") }
  var port by remember { mutableStateOf("") }

  val portNumber = port.toIntOrNull()
  val isValid = host.isNotBlank() && portNumber != null

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // No inline error state on either field -- validity is only reflected by the disabled button.
    TextField(value = host, onValueChange = { host = it }, placeholder = { Text("Hostname") })
    TextField(value = port, onValueChange = { port = it }, placeholder = { Text("Port") })

    Tooltip(tooltip = { Text("Fill in all fields") }) {
      DefaultButton(onClick = { onConnect(host, portNumber!!) }, enabled = isValid) { Text("Connect") }
    }
  }
}
