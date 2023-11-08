// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.lang.documentation.impl

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.model.psi.impl.mockEditor
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList

fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
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
  val documentationManager = DocumentationManager.getInstance(file.project)
  val (targetElement, sourceElement) = documentationManager.findTargetElementAndContext(editor, offset, file)
                                       ?: return emptyList()
  return psiDocumentationTargets(targetElement, sourceElement)
}
