/*
 * A small Markdown/code snippet renderer for a hypothetical Jewel-on-IntelliJ-bridge plugin
 * panel that shows code excerpts. Reviewers: evaluate UI-LaF vs editor-color-scheme sourcing
 * and theme/scheme change handling.
 */
package com.example.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun CodeExcerpt(code: String, modifier: Modifier = Modifier) {
  // Sources the code background from a UI panel LaF key rather than the editor color scheme.
  // Captured once with no theme/scheme-change keying, so it won't update when the user
  // switches the editor scheme (which is independent of the UI theme).
  val codeBackground = remember { retrieveColorOrUnspecified("Panel.background") }

  // Decides code-content contrast from the UI theme darkness, not the editor scheme darkness.
  val codeForeground = if (JewelTheme.isDark) Color(0xFFDFE1E5) else Color(0xFF000000)

  Text(
    text = code,
    modifier = modifier.background(codeBackground).padding(8.dp),
  )
}
