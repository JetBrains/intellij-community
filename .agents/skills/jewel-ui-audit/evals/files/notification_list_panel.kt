/*
 * A notifications tool window for a Jewel-based IntelliJ plugin. The list is often empty after the
 * user dismisses everything. Reviewers: evaluate the states this surface renders.
 */
package com.example.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.lazy.SingleSelectionLazyColumn
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.ui.component.SimpleListItem

data class Notification(val id: String, val title: String)

@Composable
fun NotificationListPanel(notifications: List<Notification>, modifier: Modifier = Modifier) {
  val state = rememberSelectableLazyListState()
  // When notifications is empty, this renders an empty column -- a blank panel, nothing else.
  SingleSelectionLazyColumn(modifier = modifier, state = state) {
    items(count = notifications.size, key = { notifications[it].id }) { index ->
      SimpleListItem(text = notifications[index].title)
    }
  }
}
