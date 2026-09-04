/*
 * A Jewel action toolbar for an IntelliJ plugin tool window: three icon-only buttons.
 * Reviewers: focus on interaction affordances for these controls.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun ActionToolbar(
  onRefresh: () -> Unit,
  onFilter: () -> Unit,
  onSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    IconButton(onClick = onRefresh) {
      Icon(key = AllIconsKeys.Actions.Refresh, contentDescription = null)
    }
    IconButton(onClick = onFilter) {
      Icon(key = AllIconsKeys.General.Filter, contentDescription = null)
    }
    IconButton(onClick = onSettings) {
      Icon(key = AllIconsKeys.General.Settings, contentDescription = null)
    }
  }
}
