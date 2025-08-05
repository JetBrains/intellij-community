// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.multiverse.CodeInsightContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

interface Problem {
  /**
   * The problem provider that the problem belongs to.
   */
  val provider: ProblemsProvider

  /**
   * One line description of the problem.
   */
  val text: String

  /**
   * A name used to group problems.
   */
  val group: String?
    get() = null

  /**
   * A name used to group problems by context.
   */
  val contextGroup: CodeInsightContext?
    @ApiStatus.Experimental
    get() = null

  /**
   * Detailed description of the problem if needed.
   */
  val description: String?
    get() = null

  /**
   * The problem icon.
   */
  val icon: Icon
    get() = HighlightDisplayLevel.ERROR.icon
}
