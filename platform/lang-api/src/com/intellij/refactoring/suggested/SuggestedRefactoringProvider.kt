// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.project.Project

interface SuggestedRefactoringProvider {
  /**
   * Resets state of accumulated signature changes used for suggesting refactoring.
   */
  fun reset()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SuggestedRefactoringProvider = project.getService(SuggestedRefactoringProvider::class.java)
  }
}