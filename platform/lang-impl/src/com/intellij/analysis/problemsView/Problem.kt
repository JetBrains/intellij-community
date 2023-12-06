// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView

import com.intellij.codeHighlighting.HighlightDisplayLevel
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
