// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingResult.Companion.EMPTY_RESULT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Identifier highlighting implementation for the local environment: when all PSI/codeinsight services are available (as opposed to the remdev environment).
 * Calls [IdentifierHighlightingComputer] directly
 */
@ApiStatus.Internal
object IdentifierHighlightingAccessorImpl : IdentifierHighlightingAccessor {
  override suspend fun getMarkupData(psiFile: PsiFile, editor: Editor, visibleRange: ProperTextRange, offset: Int): IdentifierHighlightingResult {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    return readAction {
      if (psiFile.isValid && !editor.isDisposed)
        IdentifierHighlightingComputer(psiFile, editor, visibleRange, offset).computeRanges()
      else EMPTY_RESULT
    }
  }
}