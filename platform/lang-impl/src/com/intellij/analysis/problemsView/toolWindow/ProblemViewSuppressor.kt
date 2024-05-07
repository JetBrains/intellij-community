// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * Provides utility functions for suppressing particular highlighters in Problems View's Current File tab
 */
@ApiStatus.Internal
object ProblemViewSuppressor {
  private val SUPPRESS_IN_PROBLEMS_VIEW: Key<Boolean> = Key.create("SUPPRESS_IN_PROBLEMS_VIEW")

  /**
  * Set problems view suppressing status
  */
  fun RangeHighlighter.setSuppressedInProblemView(value: Boolean) = this.putUserData(SUPPRESS_IN_PROBLEMS_VIEW, if (value) true else null)

  fun RangeHighlighter.isSuppressedInProblemsView(): Boolean = this.getUserData(SUPPRESS_IN_PROBLEMS_VIEW) ?: false
}