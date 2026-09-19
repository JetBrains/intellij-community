/*
 * A reusable empty-state header for a Jewel-based IntelliJ plugin. The same composable is rendered
 * in the IDE tool window, a standalone demo window, and screenshot tests. Reviewers: focus on
 * portability/structure across bridge and standalone contexts.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.Text

@Composable
fun EmptyStateHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier.padding(24.dp)) {
    Text(title)

    // Read a bridge-only palette key inside reusable content that also runs standalone.
    val mutedTextColor = remember { retrieveColorOrUnspecified("ColorPalette.Gray7") }

    Text(
      text = subtitle,
      color = mutedTextColor.takeOrElse { androidx.compose.ui.graphics.Color.Gray },
      modifier = Modifier.padding(top = 4.dp),
    )
  }
}

@Composable
fun EmptyStatePreview() {
  EmptyStateHeader(
    title = "No results yet",
    subtitle = "Run a search to populate this panel.",
  )
}
