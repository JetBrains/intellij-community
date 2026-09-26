// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util

import com.intellij.diff.impl.AssignmentTracker
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.UsefulTestCase
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.internal.junit.JUnitTestRule
import kotlin.test.assertEquals
import kotlin.test.fail

class AssignmentTrackerTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val edtRule = EdtRule()

  @Test
  @RunsInEdt
  fun testCallbackOrderForNestedAssignments() {
    val events = mutableListOf<String>()
    val tracker = object : AssignmentTracker() {
      override fun onEachAssignment() {
        events += "onEachAssignment"
      }

      override fun onEachUnassignment() {
        events += "onEachUnassignment"
      }

      override fun onFirstAssignment() {
        events += "onFirstAssignment"
      }

      override fun onLastUnassignment() {
        events += "onLastUnassignment"
      }
    }

    tracker.onAssigned(true)
    tracker.onAssigned(true)
    tracker.onAssigned(false)
    tracker.onAssigned(false)
    tracker.onAssigned(true)
    tracker.onAssigned(false)

    assertEquals(
      listOf(
        "onFirstAssignment",
        "onEachAssignment",
        "onEachAssignment",
        "onEachUnassignment",
        "onEachUnassignment",
        "onLastUnassignment",
        "onFirstAssignment",
        "onEachAssignment",
        "onEachUnassignment",
        "onLastUnassignment",
      ),
      events,
    )
  }

  @Test
  @RunsInEdt
  fun testExceptionFromOverriddenCallbackIsNotPropagated() {
    val tracker = object : AssignmentTracker() {
      override fun onEachAssignment() {
        throw NullPointerException("shall not be propagated")
      }
    }

    LoggedErrorProcessor.executeWith<RuntimeException>(MyLoggedErrorProcessor) {
      try {
        tracker.onAssigned(true)
      }
      catch (t: Throwable) {
        fail("Exception should not be propagated from AssignmentTracker.onAssigned: $t")
      }
    }
  }

  private object MyLoggedErrorProcessor : LoggedErrorProcessor() {
    override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): MutableSet<Action> {
      if (category.contains("AssignmentTracker") && t is NullPointerException) {
        return Action.NONE
      }
      return Action.ALL
    }
  }
}
