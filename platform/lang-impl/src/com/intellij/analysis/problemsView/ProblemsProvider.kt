// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

interface ProblemsProvider : Disposable {
  override fun dispose() {}

  /**
   * The project that the problems provider belongs to.
   */
  val project: Project
}
