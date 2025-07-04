// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Interface for running identifier highlighting computation.
 * In the case of the local monolith, redirects to [IdentifierHighlightingComputer].
 * In the case of remote dev redirects to RPC
 */
@ApiStatus.Internal
interface IdentifierHighlightingAccessor {
  @RequiresBackgroundThread
  suspend fun getMarkupData(psiFile: PsiFile, editor: Editor, visibleRange: ProperTextRange, offset: Int): IdentifierHighlightingResult

  companion object {
    fun getInstance(project: Project): IdentifierHighlightingAccessor {
      return project.getService(IdentifierHighlightingAccessor::class.java)?: IdentifierHighlightingAccessorImpl
    }
  }
}