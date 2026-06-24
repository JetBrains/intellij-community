/*
 * A heading style helper and a status row for a hypothetical Jewel panel.
 * This file is intended to be largely CORRECT. A good review should NOT invent problems here:
 * font sizes derive from the theme, colors come from Jewel globals, the list uses the right
 * built-in, and the one custom element (a colored status dot) is a justified primitive.
 * Reviewers: evaluate whether anything here actually violates design-system fidelity.
 */
package com.example.status.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** Derives a heading text style from the theme's default text style, so it scales with IDE font size. */
@Composable
fun headingStyle(): TextStyle {
  val base = JewelTheme.defaultTextStyle
  return base.copy(
    fontSize = base.fontSize * 1.5,
    lineHeight = base.fontSize * 1.5 * 1.25,
    fontWeight = FontWeight.SemiBold,
  )
}

/**
 * A small status dot. There is no Jewel primitive for "colored status indicator dot", so a tiny
 * Box with a themed fill is appropriate. The color is chosen from theme-provided semantic colors
 * by the caller, not hardcoded here.
 */
@Composable
fun StatusRow(label: String, dotColorProvider: @Composable () -> androidx.compose.ui.graphics.Color) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(8.dp),
  ) {
    Box(modifier = Modifier.size(8.dp).background(dotColorProvider(), CircleShape))
    Text(text = label)
  }
}
