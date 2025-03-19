// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.codereview.commits

import com.intellij.collaboration.ui.codereview.commits.splitCommitMessage
import org.junit.Test
import kotlin.test.assertEquals

internal class CodeReviewCommitTest {
  @Test
  fun `parse commit message title without description`() {
    val expectedTitle = "Commit title"
    val expectedDescription = ""

    val commitMessage = """
      $expectedTitle
    """.trimIndent()

    val (title, description) = splitCommitMessage(commitMessage)
    assertEquals(expectedTitle, title)
    assertEquals(expectedDescription, description)
  }

  @Test
  fun `parse commit message title with description`() {
    val expectedTitle = "Commit title"
    val expectedDescription = """
      * fixed: commit description
    """.trimIndent()

    val commitMessage = """
      $expectedTitle

      $expectedDescription
    """.trimIndent()

    val (title, description) = splitCommitMessage(commitMessage)
    assertEquals(expectedTitle, title)
    assertEquals(expectedDescription, description)
  }

  @Test
  fun `parse commit message title with long description`() {
    val expectedTitle = "Commit title"
    val expectedDescription = """
      * fixed1: commit description1
      * fixed2: commit description2
      * fixed3: commit description3
    """.trimIndent()

    val commitMessage = """
      $expectedTitle

      $expectedDescription
    """.trimIndent()

    val (title, description) = splitCommitMessage(commitMessage)
    assertEquals(expectedTitle, title)
    assertEquals(expectedDescription, description)
  }

  @Test
  fun `parse commit message title with empty description`() {
    val expectedTitle = "Commit title"
    val customDescription = " "

    val commitMessage = """
      $expectedTitle

      $customDescription
    """.trimIndent()

    val (title, description) = splitCommitMessage(commitMessage)
    assertEquals(expectedTitle, title)
    assertEquals("", description)
  }
}