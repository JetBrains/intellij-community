// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.icons.AllIcons
import javax.swing.Icon

interface Problem {
  /**
   * The problems provider that the problem belongs to.
   */
  val provider: ProblemsProvider

  /**
   * One line description of the problem.
   */
  val text: String

  /**
   * Detailed description of the problem if needed.
   */
  val description: String?
    get() = null

  /**
   * The problems icon.
   */
  val icon: Icon
    get() = AllIcons.General.InspectionsError
}
