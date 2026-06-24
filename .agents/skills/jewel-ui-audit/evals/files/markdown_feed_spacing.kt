/*
 * A feed of Markdown cards for a Jewel-based IntelliJ plugin. Reviewers: focus on spacing tokens.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.Text

@Composable
fun MarkdownFeed(cards: List<String>, styling: MarkdownStyling, modifier: Modifier = Modifier) {
  // Reuse the Markdown content-rhythm token as the card's outer layout margin.
  val gutter: Dp = styling.blockVerticalSpacing

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gutter)) {
    cards.forEach { body ->
      Column(modifier = Modifier.padding(vertical = styling.blockVerticalSpacing)) {
        Text(text = body)
      }
    }
  }
}
