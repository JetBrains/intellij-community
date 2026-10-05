/*
 * A forked list item for a Jewel-based IntelliJ plugin: a SimpleListItem with a leading
 * expand/collapse chevron -- a capability the built-in SimpleListItem cannot express.
 *
 * This file is intended to be largely CORRECT and is a PRECISION case: a good review should
 * recognize this as a JUSTIFIED fork (documented, issue-tracked, theme-correct), and NOT
 * recommend replacing it with a non-existent built-in or flag it as unjustified custom code.
 */
package com.example.plugin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * A [SimpleListItem] with a leading expand/collapse chevron. Jewel's built-in SimpleListItem has no
 * disclosure affordance, so we fork it to add one while keeping its styling, selection, and keyboard
 * semantics intact.
 *
 * TODO: remove this fork once https://youtrack.jetbrains.com/issue/JEWEL-9999 (disclosure support in
 * SimpleListItem) is resolved.
 */
@Composable
fun CollapsibleListItem(
  text: String,
  expanded: Boolean,
  selected: Boolean,
  onToggleExpanded: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier) {
    Icon(
      key = if (expanded) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
      contentDescription = if (expanded) "Collapse" else "Expand",
      modifier = Modifier.clickable(onClick = onToggleExpanded).padding(end = 4.dp),
    )
    // Reuse the built-in for the row body so styling/selection/keyboard stay identical to the platform.
    SimpleListItem(text = text, selected = selected)
  }
}
