/*
 * A badge + status pill for a hypothetical Jewel-on-IntelliJ-bridge plugin.
 * Reviewers: evaluate bridge LaF-read correctness (fallbacks, light/dark, theme-change keying)
 * and standalone-vs-bridge robustness. Assume this same composable is also exercised in a
 * standalone UI unit test where no IntelliJ LaF is installed.
 */
package com.example.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.Text

@Composable
fun StatusBadge(label: String, modifier: Modifier = Modifier) {
  // Read straight from the LaF with no fallback — returns Unspecified if the key is absent
  // (old themes, many 3p themes) or when running with no LaF at all (standalone tests).
  val bg = retrieveColorOrUnspecified("StatusBadge.background")

  // Capture a dynamic JBColor into a Compose color ONCE; it freezes at the current theme.
  val border: Color = remember { JBColor.namedColor("StatusBadge.border", JBColor.border()).toComposeColor() }

  // Single value used for both light and dark.
  val fg = Color(0xFF2B2D30)

  Text(
    text = label,
    modifier = modifier
      .background(bg, RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 2.dp),
  )
}
