/*
 * A settings preview panel for a hypothetical "Quick Notes" tool window, rendered with Jewel.
 * Reviewers: evaluate this for design-system fidelity and idiomatic IDE UX.
 */
package com.example.quicknotes.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun NotePreviewCard(title: String, body: String, modifier: Modifier = Modifier) {
  val isHovered = remember { mutableStateOf(false) }
  val elevation by animateDpAsState(if (isHovered.value) 8.dp else 0.dp)

  // Background fill for the card.
  val cardColor = JewelTheme.globalColors.borders.normal

  Column(
    modifier = modifier
      .onHover { isHovered.value = it }
      .graphicsLayer {
        shadowElevation = elevation.toPx()
        shape = RoundedCornerShape(8.dp)
        clip = false
      }
      .background(cardColor, RoundedCornerShape(8.dp))
      .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(text = title)
    Text(text = body)
  }
}

@Composable
fun NotesColumn(notes: List<Pair<String, String>>) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
    notes.forEach { (title, body) ->
      Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        NotePreviewCard(title = title, body = body, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
fun SectionSeparator() {
  // A divider between note groups.
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 16.dp)
      .background(Color.DarkGray)
      .padding(top = 6.dp),
  ) {}
}
