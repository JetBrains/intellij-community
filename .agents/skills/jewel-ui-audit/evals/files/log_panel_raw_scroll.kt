/*
 * A build-log panel for a Jewel-based IntelliJ plugin tool window. It shows an unbounded stream of
 * log lines (can be thousands) inside a fixed-height tool window. Reviewers: focus on scrolling.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

@Composable
fun BuildLogPanel(lines: List<String>, modifier: Modifier = Modifier) {
  // Raw Compose scroll: content can grow without bound, but there is no themed scrollbar.
  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
    lines.forEach { line ->
      Text(text = line)
    }
  }
}
