package com.example.plugin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TrajectorySearchResults(items: List<String>) {
  var selectedIndex by remember { mutableIntStateOf(0) }
  val listState = rememberLazyListState()

  LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }

  LazyColumn(state = listState) {
    itemsIndexed(items) { index, item -> Text(text = item, modifier = Modifier.clickable { selectedIndex = index }) }
  }
}
