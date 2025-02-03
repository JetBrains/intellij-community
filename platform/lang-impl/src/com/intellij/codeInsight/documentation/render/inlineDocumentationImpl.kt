// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider.EP_NAME
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock

@RequiresReadLock
@RequiresBackgroundThread
internal fun inlineDocumentationItems(file: PsiFile): List<InlineDocumentation> {
  val result = SmartList<InlineDocumentation>()
  for (provider in EP_NAME.extensionList) {
    result.addAll(provider.inlineDocumentationItems(file))
  }
  return result
}

@RequiresReadLock
@RequiresBackgroundThread
internal fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
  return EP_NAME.extensionList.firstNotNullOfOrNull {
    it.findInlineDocumentation(file, textRange)
  }
}
