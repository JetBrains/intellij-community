/*
 * A file-list row for a Jewel-based IntelliJ plugin tool window, hosted via JewelComposePanel
 * (bridge, swingCompatMode on by default). Reviewers: focus on the hover treatment.
 */
package com.example.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun FileRow(name: String, modifier: Modifier = Modifier) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()

  // Manually paint a hover highlight on a plain row.
  val background =
    if (isHovered) JewelTheme.globalColors.outlines.focused else androidx.compose.ui.graphics.Color.Transparent

  Row(
    modifier = modifier
      .hoverable(interactionSource)
      .background(background)
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(text = name)
  }
}
