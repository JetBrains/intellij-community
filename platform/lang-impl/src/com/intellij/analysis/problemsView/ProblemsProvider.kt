// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

interface ProblemsProvider : Disposable {
  override fun dispose() {}

  /**
   * The project that the problem provider belongs to.
   */
  val project: Project
}
