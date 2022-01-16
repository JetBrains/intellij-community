// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.InlineDocumentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock

@RequiresReadLock
@RequiresBackgroundThread
internal fun inlineDocumentationItems(file: PsiFile): List<InlineDocumentation> {
  val result = SmartList<InlineDocumentation>()
  DocumentationManager.getProviderFromElement(file).collectDocComments(file) {
    result.add(PsiCommentInlineDocumentation(it))
  }
  return result
}

@RequiresReadLock
@RequiresBackgroundThread
internal fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
  val comment = DocumentationManager.getProviderFromElement(file).findDocComment(file, textRange) ?: return null
  return PsiCommentInlineDocumentation(comment)
}
