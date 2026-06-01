// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.documentation.impl

import com.intellij.codeInsight.documentation.DocumentationTargetFinder
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.model.psi.impl.mockEditor
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList

@JvmOverloads
fun documentationTargets(file: PsiFile, offset: Int, findTargetFromLookup: Boolean = true): List<DocumentationTarget> {
  val targets = SmartList<DocumentationTarget>()
  for (ext in DocumentationTargetProvider.EP_NAME.extensionList) {
    targets.addAll(ext.documentationTargets(file, offset))
  }
  if (!targets.isEmpty()) {
    return targets
  }
  // fallback to PSI
  // TODO this fallback should be implemented inside DefaultTargetSymbolDocumentationTargetProvider, but first:
  //  - PsiSymbol has to hold information about origin element;
  //  - documentation target by argument list should be also implemented separately.
  val editor = mockEditor(file) ?: return emptyList()
  val targetWithContext = DocumentationTargetFinder.findTargetElementAndContext(file.project, editor, offset, file, findTargetFromLookup) ?: return emptyList()
  return psiDocumentationTargets(targetWithContext.target(), targetWithContext.original())
}
