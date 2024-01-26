// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList

/**
 * A provider which delegates to older [DocumentationManager]/[com.intellij.lang.documentation.DocumentationProvider] API.
 */
internal class CompatibilityInlineDocumentationProvider: InlineDocumentationProvider {

  override fun inlineDocumentationItems(file: PsiFile): Collection<InlineDocumentation> {
    val result = SmartList<InlineDocumentation>()
    DocumentationManager.getProviderFromElement(file).collectDocComments(file) {
      result.add(PsiCommentInlineDocumentation(it))
    }
    return result
  }

  override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
    val comment = DocumentationManager.getProviderFromElement(file).findDocComment(file, textRange)
                  ?: return null
    return PsiCommentInlineDocumentation(comment)
  }
}
