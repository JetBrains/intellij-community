/*
 * A three-way (nested) split layout for a Jewel-based IntelliJ plugin tool window: a left tree, a
 * center editor area, and a right inspector. Reviewers: focus on the split-pane setup.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.SplitLayoutState

@Composable
fun ThreeWayPanel(
  tree: @Composable () -> Unit,
  editor: @Composable () -> Unit,
  inspector: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Outer split: tree | (editor + inspector). State is created inline and never hoisted.
  val outerState by remember { mutableStateOf(SplitLayoutState(0.25f)) }

  HorizontalSplitLayout(
    modifier = modifier.fillMaxSize(),
    state = outerState,
    first = { tree() },
    second = {
      // Inner split: editor | inspector. Another inline, un-hoisted state.
      val innerState by remember { mutableStateOf(SplitLayoutState(0.7f)) }
      HorizontalSplitLayout(
        modifier = Modifier.fillMaxSize(),
        state = innerState,
        first = { editor() },
        second = { inspector() },
      )
    },
  )
}
