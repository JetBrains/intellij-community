// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.util.ThrowableRunnable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import javax.swing.JLabel
import kotlin.test.assertTrue

/**
 * What a form makes of a declaration that is not the ordinary case: a component belonging to no row, and a
 * row holding nothing but its comment.
 *
 * A form finds out what its children are only once it reads them all, so what it cannot place it finds out
 * about on the way to a layout rather than at the point of writing. It reports such a component the way the
 * rest of the grid reports a form it cannot make sense of - as an error in a test or an internal build, as a
 * warning in a release one - and lays out everything else, because a page missing one component is worth
 * more to whoever is looking at it than no page at all.
 */
@TestApplication
class FormPanelContractTest {

  @Test
  fun aComponentEmittedOutsideARowIsReportedAndTheRestOfTheFormIsStillLaidOut() = runComposeSwingTest {
    val reported = reportedErrorsFrom {
      setContent {
        FormPanel {
          FormRow("In a row:") { Label("in a row") }
          Label("outside every row")
        }
      }
    }

    assertTrue(
      reported.any { it.contains("belongs to a row") && it.contains("outside every row") },
      "the form should have reported the component it cannot place, but reported:\n${reported.joinToString("\n")}",
    )
    onNodeWithText("in a row").assertIsDisplayed()
    // The form gave it no cell, so nothing gave it a size either.
    assertTrue(onNodeWithText("outside every row").fetch<JLabel>().bounds.isEmpty, "the component belonging to no row is not laid out")
  }

  @Test
  fun aRowHoldingNothingButItsCommentShowsIt() = runComposeSwingTest {
    val reported = reportedErrorsFrom {
      setContent {
        FormPanel {
          // The row holds nothing else, the way a row whose only control is behind a condition holds nothing.
          FormRow(comment = "A comment with nothing above it") { }
          FormRow("After it:") { Label("after") }
        }
      }
    }

    assertTrue(reported.isEmpty(), "a row holding only its comment is a form, not a fault:\n${reported.joinToString("\n")}")
    onNodeOfType<DslLabel>().assertIsDisplayed()
  }

  /** Runs [body] and returns what it logged as an error, instead of letting it fail the test. */
  private fun reportedErrorsFrom(body: () -> Unit): List<String> {
    val reported = mutableListOf<String>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        reported += message
        return Action.NONE
      }
    }
    LoggedErrorProcessor.executeWith<Throwable>(processor, ThrowableRunnable { body() })
    return reported
  }
}
