// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal

package com.intellij.codeInsight.highlighting

import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.impl.FindManagerImpl
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.model.search.SearchService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus

internal fun highlightUsages(project: Project, editor: Editor, file: PsiFile): Boolean {
  val allTargets = targetSymbols(file, editor.caretModel.offset)
  if (allTargets.isEmpty()) {
    return false
  }
  val clearHighlights = HighlightUsagesHandler.isClearHighlights(editor)
  for (symbol in allTargets) {
    highlightSymbolUsages(project, editor, file, symbol, clearHighlights)
  }
  return true
}

private fun highlightSymbolUsages(project: Project, editor: Editor, file: PsiFile, symbol: Symbol, clearHighlights: Boolean) {
  val fileToUse = InjectedLanguageManager.getInstance(project).getTopLevelFile((file as? PsiCompiledFile)?.decompiledPsiFile ?: file)
  val editorToUse = (editor as? EditorWindow)?.delegate ?: editor
  val (readRanges, writeRanges, readDeclarationRanges, writeDeclarationRanges) = getUsageRanges(fileToUse, symbol)
  HighlightUsagesHandler.highlightUsages(
    project, editorToUse,
    readRanges + readDeclarationRanges,
    writeRanges + writeDeclarationRanges,
    clearHighlights
  )
  HighlightUsagesHandler.setStatusText(project, null, readRanges.size + writeRanges.size, clearHighlights)
}

internal fun getUsageRanges(file: PsiFile, symbol: Symbol): UsageRanges {
  val readRanges = ArrayList<TextRange>()
  val writeRanges = ArrayList<TextRange>()
  val readDeclarationRanges = ArrayList<TextRange>()
  val writeDeclarationRanges = ArrayList<TextRange>()

  val searchScope: SearchScope = LocalSearchScope(file)
  val project: Project = file.project
  val psiTarget: PsiElement? = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
  val detector: ReadWriteAccessDetector? = if (psiTarget != null) ReadWriteAccessDetector.findDetector(psiTarget) else null
  val refs: Collection<PsiSymbolReference> = getReferences(project, searchScope, symbol, psiTarget)
  for (ref: PsiSymbolReference in refs) {
    val write: Boolean = detector != null &&
                         ref is PsiReference &&
                         detector.getReferenceAccess(psiTarget!!, ref) != ReadWriteAccessDetector.Access.Read
    HighlightUsagesHandler.collectHighlightRanges(ref, if (write) writeRanges else readRanges)
  }
  val declarations: Collection<PsiSymbolDeclaration> = SearchService.getInstance()
    .searchPsiSymbolDeclarations(project, symbol, searchScope)
    .findAll()
  val declarationWrite: Boolean = (psiTarget != null) && (detector != null) && detector.isDeclarationWriteAccess(psiTarget)
  for (declaration: PsiSymbolDeclaration in declarations) {
    HighlightUsagesHandler.collectHighlightRanges(
      declaration.declaringElement, declaration.declarationRange, if (declarationWrite) writeDeclarationRanges else readDeclarationRanges
    )
  }

  return UsageRanges(readRanges, writeRanges, readDeclarationRanges, writeDeclarationRanges)
}

private fun getReferences(project: Project,
                          searchScope: SearchScope,
                          symbol: Symbol,
                          psiTarget: PsiElement?): Collection<PsiSymbolReference> {
  if (psiTarget != null) {
    val oldHandler: FindUsagesHandler? = (FindManager.getInstance(project) as FindManagerImpl)
      .findUsagesManager
      .getFindUsagesHandler(psiTarget, true)
    if (oldHandler != null) {
      return oldHandler.findReferencesToHighlight(psiTarget, searchScope)
    }
  }
  return SearchService.getInstance()
    .searchPsiSymbolReferences(project, symbol, searchScope)
    .findAll()
}
