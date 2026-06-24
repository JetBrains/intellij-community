/*
 * A row of clickable "tag" chips for a Jewel-based IntelliJ plugin. Chips are keyboard-focusable.
 * Reviewers: focus on interaction affordances (focus, keyboard) and theming.
 */
package com.example.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TagChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val borderColor = if (selected) Color(0xFF3574F0) else JewelTheme.globalColors.borders.normal
  Row(
    modifier = modifier
      .clickable(onClick = onClick)
      .focusable() // reachable by Tab, but nothing is drawn when focused
      .border(1.dp, borderColor, RoundedCornerShape(12.dp))
      .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(12.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(text = label)
  }
}

@Composable
fun TagChipRow(tags: List<String>, selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    tags.forEach { tag ->
      TagChip(label = tag, selected = tag in selected, onClick = { onToggle(tag) })
    }
  }
}
