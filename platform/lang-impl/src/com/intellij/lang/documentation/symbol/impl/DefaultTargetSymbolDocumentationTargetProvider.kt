// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.symbol.impl

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.DocumentationTargetProvider
import com.intellij.lang.documentation.symbol.DocumentationSymbol
import com.intellij.lang.documentation.symbol.SymbolDocumentationTargetProvider
import com.intellij.model.Symbol
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.project.Project
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
  return targetSymbols.mapNotNullTo(LinkedHashSet()) {
    symbolDocumentationTarget(project, it)
  }.toList()
}

internal fun symbolDocumentationTarget(project: Project, symbol: Symbol): DocumentationTarget? {
  for (ext in SymbolDocumentationTargetProvider.EP_NAME.extensionList) {
    return ext.documentationTarget(project, symbol) ?: continue
  }
  if (symbol is DocumentationSymbol) {
    return symbol.documentationTarget
  }
  if (symbol is DocumentationTarget) {
    return symbol
  }
  return null
}
