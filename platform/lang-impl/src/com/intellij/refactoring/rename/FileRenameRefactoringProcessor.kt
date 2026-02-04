// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo

/**
 * Same as [RenameProcessor] except marked as DumbAware.
 * Used to allow renaming files and directories in dumb mode.
 */
internal class FileRenameRefactoringProcessor(
  project: Project,
  element: PsiElement,
  newName: String,
  refactoringScope: SearchScope,
  isSearchInComments: Boolean,
  isSearchTextOccurrences: Boolean,
) : RenameProcessor(project, element, newName, refactoringScope, isSearchInComments, isSearchTextOccurrences), DumbAware {
  override fun findUsages(): Array<out UsageInfo?> {
    // TODO: rethink this
    return emptyArray()
  }
}