// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.components.DslLabel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentTest {

  @Test
  fun commentRendersAndUpdatesText() = runComposeSwingTest {
    var text by mutableStateOf("Initial comment")

    setContent {
      Comment(text = text)
    }

    onNodeOfType<DslLabel>().apply {
      assertTrue(fetch().text.contains("Initial comment"))
    }

    text = "Updated comment"
    awaitIdle()

    onNodeOfType<DslLabel>().apply {
      assertTrue(fetch().text.contains("Updated comment"))
    }
  }

  @Test
  fun commentWrapsToTheWidthItIsGivenByDefault() = runComposeSwingTest {
    setContent {
      Comment(text = "A comment")
    }

    val comment = onNodeOfType<DslLabel>().fetch()
    assertEquals(MAX_LINE_LENGTH_WORD_WRAP, comment.maxLineLength)
    assertTrue(comment.limitPreferredSize)
  }

  @Test
  fun commentWithAFixedWidthAsksForTheWidthItsTextNeeds() = runComposeSwingTest {
    setContent {
      Comment(text = "A comment", maxLineLength = DEFAULT_COMMENT_WIDTH)
    }

    val comment = onNodeOfType<DslLabel>().fetch()
    assertEquals(DEFAULT_COMMENT_WIDTH, comment.maxLineLength)
    assertFalse(comment.limitPreferredSize)
  }
}
