// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ThrowableRunnable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a form makes of a declaration that is not the ordinary case: a component belonging to no row, a row
 * holding nothing but its comment, and a comment that changes or meets a description set elsewhere.
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
  fun theRowLabelFollowsItsFirstControlAndPosition() = runComposeSwingTest {
    var firstVisible by mutableStateOf(true)
    var label by mutableStateOf("Name:")
    var position by mutableStateOf(FormLabelPosition.LEFT)
    setContent {
      FormPanel {
        FormRow(label, labelPosition = position) {
          if (firstVisible) {
            SwingNode(factory = { JTextField(10) }, modifier = SwingModifier.testTag("first"))
          }
          SwingNode(factory = { JTextField(10) }, modifier = SwingModifier.testTag("second"))
        }
      }
    }
    val first = onNodeWithTag("first").fetch<JTextField>()
    val second = onNodeWithTag("second").fetch<JTextField>()
    assertEquals("Name:", first.accessibleContext.accessibleName)
    assertNull(second.accessibleContext.accessibleName)

    label = "Path:"
    position = FormLabelPosition.TOP
    awaitIdle()
    assertEquals("Path:", first.accessibleContext.accessibleName)

    firstVisible = false
    awaitIdle()
    assertEquals("Path:", second.accessibleContext.accessibleName)
    assertNull(first.accessibleContext.accessibleName)
  }

  @Test
  fun conflictingWidthGroupAndFillIsReportedWhenTheFormLaysTheControlOut() = runComposeSwingTest {
    val failure = assertFailsWith<IllegalStateException> {
      setContent {
        FormPanel(modifier = SwingModifier.testTag("form")) {
          FormRow {
            SwingNode(
              factory = { JTextField(10) },
              modifier = SwingModifier.testTag("field").widthGroup("buttons").align(FormAlignX.FILL),
            )
          }
        }
      }
      onNodeWithTag("form").fetch<JComponent>().preferredSize
    }
    val message = failure.message!!
    assertTrue(message.contains("JTextField"), "the message names the control: $message")
    assertTrue(message.contains("Drop widthGroup or align(FormAlignX.FILL)"), message)
  }

  @Test
  fun modifiersOnOneChainCombine() = runComposeSwingTest {
    setContent {
      FormPanel(modifier = SwingModifier.testTag("form")) {
        FormRow {
          SwingNode(
            factory = { JTextField(10) },
            modifier = SwingModifier.testTag("field").align(FormAlignX.FILL).align(FormAlignY.FILL).gap(FormRightGap.COLUMNS),
          )
          SwingNode(factory = { JTextField(10) }, modifier = SwingModifier.testTag("plain"))
          Label("after")
        }
      }
    }
    val layout = onNodeWithTag("form").fetch<JComponent>().layout as GridLayout
    val constraints = checkNotNull(layout.getConstraints(onNodeWithTag("field").fetch<JTextField>()))
    val plain = checkNotNull(layout.getConstraints(onNodeWithTag("plain").fetch<JTextField>()))
    assertEquals(HorizontalAlign.FILL, constraints.horizontalAlign)
    assertEquals(VerticalAlign.FILL, constraints.verticalAlign)
    assertTrue(constraints.gaps.right > plain.gaps.right, "the column gap is wider than the default one: ${constraints.gaps} vs ${plain.gaps}")
  }

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

  @Test
  fun aRowWithATopLabelAndNoControlsShowsItsLabel() = runComposeSwingTest {
    val reported = reportedErrorsFrom {
      setContent {
        FormPanel {
          // The row holds nothing else, the way a row whose only control is behind a condition holds nothing.
          FormRow("Only a label:", labelPosition = FormLabelPosition.TOP) { }
          FormRow("After it:") { Label("after") }
        }
      }
    }

    assertTrue(reported.isEmpty(), "a row holding only a top label is a form, not a fault:\n${reported.joinToString("\n")}")
    onNodeWithText("Only a label:").assertIsDisplayed()
  }

  @Test
  fun changingOnlyTheCommentWrapWidthRelaysTheForm() = runComposeSwingTest {
    var maxLineLength by mutableStateOf(MAX_LINE_LENGTH_WORD_WRAP)
    setContent {
      FormPanel(modifier = SwingModifier.testTag("form")) {
        // A comment standing alone fills the form only when the form is the one that wraps it.
        FormRow(comment = "A short comment", commentMaxLineLength = maxLineLength) { }
        // A wide row, so the form is wider than the comment asks to be.
        FormRow { SwingNode(factory = { JTextField(40) }) }
      }
    }
    val comment = onNodeOfType<DslLabel>().fetch()
    val wrappedByTheForm = comment.width
    val formWidth = onNodeWithTag("form").fetch<JComponent>().width
    assertTrue(wrappedByTheForm > formWidth / 2, "the form wraps the comment, but it is $wrappedByTheForm of $formWidth")

    maxLineLength = DEFAULT_COMMENT_WIDTH
    awaitIdle()
    assertTrue(comment.width < wrappedByTheForm, "the comment keeps its own width, but is ${comment.width} of $wrappedByTheForm")
  }

  @Test
  fun recomposingARowWithEqualCellOptionsKeepsTheGrid() = runComposeSwingTest {
    var tick by mutableStateOf(0)
    setContent {
      FormPanel(modifier = SwingModifier.testTag("form")) {
        FormRow {
          // Every composition makes a new modifier, equal to the last one in what it says.
          SwingNode(
            factory = { JTextField(10) },
            modifier = SwingModifier.testTag("field").align(FormAlignX.FILL).align(FormAlignY.FILL).gap(FormRightGap.SMALL),
          )
          Label("tick $tick")
        }
      }
    }
    val form = onNodeWithTag("form").fetch<JComponent>()
    val field = onNodeWithTag("field").fetch<JTextField>()
    val layout = form.layout as GridLayout
    val before = layout.getConstraints(field)

    tick = 1
    awaitIdle()
    onNodeWithText("tick 1").assertIsDisplayed()
    assertSame(before, layout.getConstraints(field), "the grid was built again though no cell option changed")
  }

  @Test
  fun theDescriptionOfTheFirstControlFollowsTheComment() = runComposeSwingTest {
    var comment by mutableStateOf<String?>("Use with caution")
    setContent {
      FormPanel {
        FormRow(comment = comment) { SwingNode(factory = { JCheckBox("Brave mode") }) }
      }
    }
    assertEquals("Use with caution", checkBoxDescription())

    comment = "Use with great caution"
    awaitIdle()
    assertEquals("Use with great caution", checkBoxDescription())

    comment = null
    awaitIdle()
    assertNull(checkBoxDescription())
  }

  @Test
  fun aDescriptionSetElsewhereIsKept() = runComposeSwingTest {
    setContent {
      FormPanel {
        FormRow(comment = "Use with caution") {
          SwingNode(factory = { JCheckBox("Brave mode").apply { accessibleContext.accessibleDescription = "Set by the caller" } })
        }
      }
    }
    assertEquals("Set by the caller", checkBoxDescription())
  }

  private fun ComposeSwingTest.checkBoxDescription(): String? =
    onNodeOfType<JCheckBox>().fetch().accessibleContext.accessibleDescription

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
