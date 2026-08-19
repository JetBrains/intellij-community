// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Experimental
abstract class PerformanceAssertions {

  abstract fun checkDoesNotAffectHighlighting()

  companion object {

    private val assertDoesNotAffectHighlightingSuppressed = ThreadLocal<Boolean>()

    /**
     * Checks if current code is not called from a performance-critical path
     * e.g., code highlighting, [com.intellij.psi.PsiElement.getReference].
     *
     * It is similar to [SlowOperations.assertSlowOperationsAreAllowed] but imposes more strict restrictions.
     */
    @JvmStatic
    fun assertDoesNotAffectHighlighting() {
      ApplicationManager.getApplication().service<PerformanceAssertions>().checkDoesNotAffectHighlighting()
    }

    /**
     * Temporarily suppresses the highlighting-pass part of [assertDoesNotAffectHighlighting] for a known issue.
     * The suppression is scoped to the current thread and must be closed after the affected operation finishes.
     */
    @Internal
    @JvmStatic
    fun suppressAssertDoesNotAffectHighlighting(knownIssueId: String): AccessToken {
      require(knownIssueId.isNotBlank()) { "A known issue ID must be specified" }
      val previousValue = assertDoesNotAffectHighlightingSuppressed.get()
      assertDoesNotAffectHighlightingSuppressed.set(true)
      return object : AccessToken() {
        override fun finish() {
          if (previousValue == null) {
            assertDoesNotAffectHighlightingSuppressed.remove()
          }
          else {
            assertDoesNotAffectHighlightingSuppressed.set(previousValue)
          }
        }
      }
    }

    @Internal
    @JvmStatic
    fun isAssertDoesNotAffectHighlightingSuppressed(): Boolean {
      return true == assertDoesNotAffectHighlightingSuppressed.get()
    }

  }

}