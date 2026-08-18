// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.util.ui.ThreeStateCheckBox.State
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.intellij.util.ui.ThreeStateCheckBox as IdeaThreeStateCheckBox

class ThreeStateCheckBoxTest {

  @Test
  fun clickingCyclesThroughEveryStateAndReportsIt() = runComposeSwingTest {
    var state by mutableStateOf(ThreeStateCheckBoxState.NOT_SELECTED)
    val reported = mutableListOf<ThreeStateCheckBoxState>()

    setContent {
      ThreeStateCheckBox(
        text = "Include subdirectories",
        state = state,
        onStateChange = {
          reported += it
          state = it
        },
      )
    }

    val checkBox = onNodeOfType<IdeaThreeStateCheckBox>()
    assertEquals("Include subdirectories", checkBox.fetch().text)
    assertEquals(State.NOT_SELECTED, checkBox.fetch().state)

    checkBox.performClick()
    assertEquals(State.DONT_CARE, checkBox.fetch().state)

    checkBox.performClick()
    assertEquals(State.SELECTED, checkBox.fetch().state)

    checkBox.performClick()
    assertEquals(State.NOT_SELECTED, checkBox.fetch().state)

    assertEquals(
      listOf(
        ThreeStateCheckBoxState.INDETERMINATE,
        ThreeStateCheckBoxState.SELECTED,
        ThreeStateCheckBoxState.NOT_SELECTED,
      ),
      reported,
    )
  }

  @Test
  fun stateDeclaredFromOutsideMovesTheBoxWithoutBeingReported() = runComposeSwingTest {
    var state by mutableStateOf(ThreeStateCheckBoxState.INDETERMINATE)
    var reports = 0

    setContent {
      ThreeStateCheckBox(text = "Include subdirectories", state = state, onStateChange = { reports++ })
    }

    assertEquals(State.DONT_CARE, onNodeOfType<IdeaThreeStateCheckBox>().fetch().state)

    state = ThreeStateCheckBoxState.SELECTED
    awaitIdle()
    assertEquals(State.SELECTED, onNodeOfType<IdeaThreeStateCheckBox>().fetch().state)

    state = ThreeStateCheckBoxState.NOT_SELECTED
    awaitIdle()
    assertEquals(State.NOT_SELECTED, onNodeOfType<IdeaThreeStateCheckBox>().fetch().state)

    assertEquals(0, reports)
  }

  @Test
  fun aClickTheCallerDoesNotAdoptIsPutBack() = runComposeSwingTest {
    var reported: ThreeStateCheckBoxState? = null

    setContent {
      ThreeStateCheckBox(
        text = "Include subdirectories",
        state = ThreeStateCheckBoxState.SELECTED,
        onStateChange = { reported = it },
      )
    }

    onNodeOfType<IdeaThreeStateCheckBox>().performClick()

    assertEquals(ThreeStateCheckBoxState.NOT_SELECTED, reported)
    assertEquals(State.SELECTED, onNodeOfType<IdeaThreeStateCheckBox>().fetch().state)
  }
}
