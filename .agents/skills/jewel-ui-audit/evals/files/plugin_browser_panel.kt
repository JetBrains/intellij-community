/*
 * An installed-plugins list for a Jewel-based IntelliJ plugin settings panel (~100 rows).
 * Reviewers: evaluate the list implementation for the IDE context.
 */
package com.example.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.lazy.SingleSelectionLazyColumn
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.ui.component.SimpleListItem

data class PluginInfo(val id: String, val name: String)

@Composable
fun PluginBrowserPanel(plugins: List<PluginInfo>, modifier: Modifier = Modifier) {
  val state = rememberSelectableLazyListState()
  // Correct selection container + row, but no way to type-to-filter a ~100-item list.
  SingleSelectionLazyColumn(modifier = modifier, state = state) {
    items(count = plugins.size, key = { plugins[it].id }) { index ->
      SimpleListItem(text = plugins[index].name)
    }
  }
}
