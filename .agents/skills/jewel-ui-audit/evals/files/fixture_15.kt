/*
 * A search-results feed for a Jewel-based IntelliJ plugin tool window. The whole feed is made
 * text-selectable so users can copy file paths. Reviewers: focus on selection scope and pointer.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

data class FileResult(val name: String, val path: String)

@Composable
fun FileResultsFeed(results: List<FileResult>, onJump: (FileResult) -> Unit, modifier: Modifier = Modifier) {
  // Wrap the entire list so any text anywhere can be selected and copied.
  SelectionContainer {
    LazyColumn(modifier = modifier) {
      items(results.size) { index ->
        val result = results[index]
        Row(modifier = Modifier.padding(8.dp)) {
          Icon(key = AllIconsKeys.FileTypes.Text, contentDescription = null)
          Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = result.name)
            Text(text = result.path)
          }
          IconButton(onClick = { onJump(result) }) { Icon(key = AllIconsKeys.Actions.Forward, contentDescription = "Jump to source") }
        }
        Divider(orientation = Orientation.Horizontal)
      }
    }
  }
}
