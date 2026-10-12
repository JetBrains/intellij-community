/*
 * A compact row for a Jewel-based IntelliJ plugin. Reviewers: focus on icon/image
 * contentDescription choices and which elements should be hidden from accessibility.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun PluginStatusRow(pluginName: String, enabled: Boolean, onConfigure: () -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier.padding(8.dp)) {
    Icon(key = AllIconsKeys.Nodes.Plugin, contentDescription = pluginName)

    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
      Text(pluginName)
      Row {
        Icon(key = AllIconsKeys.General.InspectionsOK, contentDescription = null)
        Text(if (enabled) "Enabled" else "Disabled")
      }
    }

    IconButton(onClick = onConfigure) {
      Icon(key = AllIconsKeys.General.GearPlain, contentDescription = "")
    }
  }
}
