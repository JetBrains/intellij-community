/*
 * A documentation / release-notes panel for a Jewel-based IntelliJ plugin. It renders long-form
 * Markdown prose inside a vertically scrolling column. The panel is shown in a tool window that the
 * user can widen to the full editor width. Reviewers: focus on the prose text layout.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ReleaseNotesPanel(paragraphs: List<String>, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
  ) {
    paragraphs.forEach { paragraph ->
      // Prose fills the full panel width; on a wide tool window this is hundreds of chars per line.
      Text(text = paragraph, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
  }
}
