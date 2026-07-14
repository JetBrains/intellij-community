/*
 * A label that paints itself with the "primary" accent from the Jewel color palette, for a
 * Jewel-based IntelliJ plugin. Reviewers: focus on palette-index usage and portability.
 */
package com.example.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
fun AccentLabel(text: String, modifier: Modifier = Modifier) {
  // Blue4 is always the primary blue; Gray1 is the primary text color.
  val accent = JewelTheme.colorPalette.blue(4)
  val textColor = JewelTheme.colorPalette.gray(1)

  Text(text = text, color = accent, modifier = modifier)
}
