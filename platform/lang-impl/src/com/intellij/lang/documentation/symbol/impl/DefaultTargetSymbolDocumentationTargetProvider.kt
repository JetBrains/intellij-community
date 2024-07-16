// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.symbol.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.SymbolDocumentationTargetProvider
import com.intellij.psi.PsiFile

/**
 * A [DocumentationTargetProvider] which delegates to [SymbolDocumentationTargetProvider]
 */
internal class DefaultTargetSymbolDocumentationTargetProvider : DocumentationTargetProvider {

  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    return symbolDocumentationTargets(file, offset)
  }
}

private fun symbolDocumentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
  val symbols = targetSymbols(file, offset)
  return symbolDocumentationTargets(file.project, symbols)
}

internal fun symbolDocumentationTargets(project: Project, targetSymbols: Collection<Symbol>): List<DocumentationTarget> {
  return targetSymbols.flatMapTo(LinkedHashSet()) {
    symbolDocumentationTargets(project, it)
  }.toList()
}

internal fun symbolDocumentationTargets(project: Project, symbol: Symbol): List<DocumentationTarget> {
  for (ext in SymbolDocumentationTargetProvider.EP_NAME.extensionList) {
    val targets = ext.documentationTargets(project, symbol)
    if (!targets.isEmpty()) {
      return targets
    }
  }
  if (symbol is DocumentationSymbol) {
    return listOf(symbol.documentationTarget)
  }
  if (symbol is DocumentationTarget) {
    return listOf(symbol)
  }
  return emptyList()
}
