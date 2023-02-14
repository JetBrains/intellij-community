// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.codeInsight.highlighting

import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.impl.FindManagerImpl
import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageOptions
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildQuery
import com.intellij.find.usages.impl.symbolSearchTarget
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
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
  val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
  val (readRanges, writeRanges, readDeclarationRanges, writeDeclarationRanges) = getUsageRanges(file, symbol) ?: return
  HighlightUsagesHandler.highlightUsages(
    project, hostEditor,
    readRanges + readDeclarationRanges,
    writeRanges + writeDeclarationRanges,
    clearHighlights
  )
  HighlightUsagesHandler.setStatusText(project, null, readRanges.size + writeRanges.size, clearHighlights)
}

internal fun getUsageRanges(file: PsiFile, symbol: Symbol): UsageRanges? {
  val psiTarget: PsiElement? = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
  if (psiTarget != null) {
    return getPsiUsageRanges(file, psiTarget)
  }
  else {
    return getSymbolUsageRanges(file, symbol)
  }
}

private fun getPsiUsageRanges(file: PsiFile, psiTarget: PsiElement): UsageRanges {
  val readRanges = ArrayList<TextRange>()
  val writeRanges = ArrayList<TextRange>()
  val readDeclarationRanges = ArrayList<TextRange>()
  val writeDeclarationRanges = ArrayList<TextRange>()

  val project = file.project
  val hostFile: PsiFile = psiTarget.containingFile?.let { targetContainingFile ->
    val injectedManager = InjectedLanguageManager.getInstance(project)
    if (injectedManager.isInjectedFragment(file) != injectedManager.isInjectedFragment(targetContainingFile)) {
      // weird case when injected symbol references host file
      injectedManager.getTopLevelFile(file)
    }
    else {
      null
    }
  } ?: file
  val searchScope: SearchScope = LocalSearchScope(hostFile)
  val detector: ReadWriteAccessDetector? = ReadWriteAccessDetector.findDetector(psiTarget)
  val oldHandler: FindUsagesHandler? = (FindManager.getInstance(project) as FindManagerImpl)
    .findUsagesManager
    .getFindUsagesHandler(psiTarget, true)
  val refs = oldHandler?.findReferencesToHighlight(psiTarget, searchScope)
             ?: ReferencesSearch.search(psiTarget, searchScope).findAll()
  for (ref: PsiReference in refs) {
    val write: Boolean = detector != null && detector.getReferenceAccess(psiTarget, ref) != ReadWriteAccessDetector.Access.Read
    HighlightUsagesHandler.collectHighlightRanges(ref, if (write) writeRanges else readRanges)
  }

  val declRange = HighlightUsagesHandler.getNameIdentifierRange(hostFile, psiTarget)
  if (declRange != null) {
    val write = detector != null && detector.isDeclarationWriteAccess(psiTarget)
    if (write) {
      writeDeclarationRanges.add(declRange)
    }
    else {
      readDeclarationRanges.add(declRange)
    }
  }

  return UsageRanges(readRanges, writeRanges, readDeclarationRanges, writeDeclarationRanges)
}

private fun getSymbolUsageRanges(file: PsiFile, symbol: Symbol): UsageRanges? {
  val project: Project = file.project
  val searchTarget = symbolSearchTarget(project, symbol) ?: return null
  val searchScope = LocalSearchScope(file)
  val usages: Collection<Usage> = buildQuery(project, searchTarget, AllSearchOptions(
    options = UsageOptions.createOptions(searchScope),
    textSearch = true,
  )).findAll()
  val readRanges = ArrayList<TextRange>()
  val readDeclarationRanges = ArrayList<TextRange>()
  for (usage in usages) {
    if (usage !is PsiUsage) {
      continue
    }
    HighlightUsagesHandler.collectHighlightRanges(usage.file, usage.range, if (usage.declaration) readDeclarationRanges else readRanges)
  }
  return UsageRanges(readRanges, emptyList(), readDeclarationRanges, emptyList())
}
