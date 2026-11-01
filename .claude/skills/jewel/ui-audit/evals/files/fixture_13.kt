/*
 * A reusable search-results panel for a Jewel-based IntelliJ plugin. It is also rendered in a
 * standalone UI unit test and in an @Preview. Reviewers: focus on portability/structure across
 * the bridge and standalone contexts.
 */
package com.example.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SearchResultsPanel(matches: List<String>, modifier: Modifier = Modifier) {
  // Wrap our own content in the bridge theme, then render.
  SwingBridgeTheme {
    // Read the match-highlight color straight from the LaF, inline.
    val highlight = retrieveColorOrUnspecified("SearchMatch.background")

    Column(modifier = modifier) {
      matches.forEach { line ->
        Text(text = line, modifier = Modifier.background(highlight.takeOrElse { androidx.compose.ui.graphics.Color.Yellow }).padding(2.dp))
      }
    }
  }
}

// Used elsewhere in the same content file to look up the active theme.
private fun describeTheme(laf: UIThemeBasedLookAndFeelInfo): String = laf.name
