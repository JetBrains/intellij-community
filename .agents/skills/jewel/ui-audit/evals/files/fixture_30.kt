/*
 * A resizable detail pane for a Jewel-based IntelliJ plugin tool window. It holds a header row
 * (avatar + title + a right-aligned action button) and a body. The whole pane fills whatever width
 * the surrounding split/tool-window gives it. Reviewers: focus on resize behavior.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun DetailPane(title: String, body: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
  // No minimum size: the pane shrinks to whatever the parent offers, all the way to zero.
  Column(modifier = modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(key = AllIconsKeys.General.User, contentDescription = null)
      Text(text = title, modifier = Modifier.weight(1f))
      OutlinedButton(onClick = onAction) { Text("Configure") }
    }
    Text(text = body)
  }
}
