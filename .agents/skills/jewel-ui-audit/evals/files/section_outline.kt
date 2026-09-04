/*
 * A document outline side panel for a hypothetical "Release Notes" viewer, rendered with Jewel.
 * The left panel lists sections; clicking one scrolls the main content; the currently-visible
 * section is highlighted in the list.
 * Reviewers: evaluate selection/interaction behavior and component choice.
 */
package com.example.releasenotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

data class Section(val id: String, val title: String)

/** Tracks which section is "active" based on what's scrolled to the top of the content list. */
class SectionTracker(private val sections: List<Section>, private val listState: LazyListState) {
  val activeSection: State<Section> = derivedStateOf { sections[listState.firstVisibleItemIndex] }

  suspend fun scrollTo(section: Section) {
    val index = sections.indexOf(section)
    if (index >= 0) listState.animateScrollToItem(index)
  }
}

@Composable
fun SectionOutline(sections: List<Section>, tracker: SectionTracker) {
  val active by tracker.activeSection
  val scope = rememberCoroutineScope()

  VerticallyScrollableContainer {
    Column(modifier = Modifier.padding(8.dp)) {
      sections.forEach { section ->
        SimpleListItem(
          selected = section == active,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { scope.launch { tracker.scrollTo(section) } },
        ) {
          Text(section.title, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
      }
    }
  }
}
