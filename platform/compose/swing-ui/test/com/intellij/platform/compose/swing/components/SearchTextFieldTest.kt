// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.ui.components.JBTextField
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.intellij.ui.SearchTextField as IdeaSearchTextField

class SearchTextFieldTest {

  @Test
  fun searchFieldShowsTheDeclaredText() = runComposeSwingTest {
    var text by mutableStateOf("initial query")

    setContent {
      SearchTextField(text = text, onTextChange = { text = it })
    }

    assertEquals("initial query", onNodeOfType<IdeaSearchTextField>().fetch().text)

    text = "declared query"
    awaitIdle()

    assertEquals("declared query", onNodeOfType<IdeaSearchTextField>().fetch().text)
  }

  @Test
  fun editingTheFieldReportsTheEditedText() = runComposeSwingTest {
    var text by mutableStateOf("")
    val reported = mutableListOf<String>()

    setContent {
      SearchTextField(text = text, onTextChange = { reported += it; text = it })
    }

    onNodeOfType<JBTextField>().performTextReplacement("typed query")

    assertEquals(listOf("typed query"), reported)
    assertEquals("typed query", text)
    assertEquals("typed query", onNodeOfType<IdeaSearchTextField>().fetch().text)
  }

  @Test
  fun applyingTheDeclaredTextIsNotReportedAsAnEdit() = runComposeSwingTest {
    var text by mutableStateOf("initial query")
    val reported = mutableListOf<String>()

    setContent {
      SearchTextField(text = text, onTextChange = { reported += it })
    }

    text = "declared query"
    awaitIdle()

    assertEquals("declared query", onNodeOfType<IdeaSearchTextField>().fetch().text)
    assertTrue(reported.isEmpty(), "Expected no edits to be reported, got $reported")
  }

  @Test
  fun aFieldWithoutAHistoryPropertyNameKeepsItsHistoryInMemory() = runComposeSwingTest {
    var text by mutableStateOf("")

    setContent {
      SearchTextField(text = text, onTextChange = { text = it })
    }

    onNodeOfType<JBTextField>().performTextReplacement("remembered query")
    onNodeOfType<JBTextField>().performFocusLost()

    assertEquals(listOf("remembered query"), onNodeOfType<IdeaSearchTextField>().fetch().history)
  }
}
