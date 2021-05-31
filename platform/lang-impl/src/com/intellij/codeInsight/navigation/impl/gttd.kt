// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.codeInsight.navigation.MultipleTargetElementsInfo
import com.intellij.codeInsight.navigation.SymbolTypeProvider
import com.intellij.codeInsight.navigation.actions.TypeDeclarationPlaceAwareProvider
import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.codeInsight.navigation.impl.GTTDActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.GTTDActionResult.SingleTarget
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.EvaluatorReference
import com.intellij.model.psi.impl.TargetData
import com.intellij.model.psi.impl.declaredReferencedData
import com.intellij.model.psi.impl.mockEditor
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.SymbolNavigationService
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList

internal fun gotoTypeDeclaration(file: PsiFile, offset: Int): GTTDActionData? {
  return processInjectionThenHost(file, offset, ::gotoTypeDeclarationInner)
}

/**
 * "Go To Type Declaration" action result
 */
internal sealed class GTTDActionResult {

  class SingleTarget(val navigatable: () -> Navigatable) : GTTDActionResult()

  class MultipleTargets(val targets: List<SingleTargetWithPresentation>) : GTTDActionResult() {
    init {
      require(targets.isNotEmpty())
    }
  }
}

internal data class SingleTargetWithPresentation(val navigatable: () -> Navigatable, val presentation: TargetPresentation)

private fun gotoTypeDeclarationInner(file: PsiFile, offset: Int): GTTDActionData? {
  val (declaredData, referencedData) = declaredReferencedData(file, offset) ?: return null
  val targetData = referencedData ?: declaredData ?: return null
  val editor = mockEditor(file) ?: return null
  return GTTDActionData(file.project, targetData, editor, offset)
}

/**
 * "Go To Type Declaration" action data
 */
internal class GTTDActionData(
  private val project: Project,
  private val targetData: TargetData,
  private val editor: Editor,
  private val offset: Int,
) {

  private fun typeSymbols() = targetData.typeSymbols(editor, offset)

  fun ctrlMouseInfo(): CtrlMouseInfo? {
    val typeSymbols = typeSymbols().take(2).toList()
    return when (typeSymbols.size) {
      0 -> null
      1 -> SingleSymbolCtrlMouseInfo(typeSymbols.single(), targetData.elementAtOffset(), targetData.highlightRanges())
      else -> MultipleTargetElementsInfo(targetData.highlightRanges())
    }
  }

  fun result(): GTTDActionResult? {
    return result(typeSymbols().navigationTargets(project).toCollection(SmartList()))
  }
}

private fun TargetData.typeSymbols(editor: Editor, offset: Int): Sequence<Symbol> {
  return when (this) {
    is TargetData.Declared -> typeSymbols(editor, offset)
    is TargetData.Referenced -> typeSymbols(editor, offset)
  }
}

private fun TargetData.Declared.typeSymbols(editor: Editor, offset: Int): Sequence<Symbol> = sequence {
  val psiSymbolService = PsiSymbolService.getInstance()
  val psi = psiSymbolService.extractElementFromSymbol(declaration.symbol)
  if (psi != null) {
    for (typeElement in typeElements(editor, offset, psi)) {
      yield(psiSymbolService.asSymbol(typeElement))
    }
  }
  else {
    for (typeProvider in SymbolTypeProvider.EP_NAME.extensions) {
      yieldAll(typeProvider.getSymbolTypes(declaration.symbol))
    }
  }
}

private fun TargetData.Referenced.typeSymbols(editor: Editor, offset: Int): Sequence<Symbol> = sequence {
  for (reference: PsiSymbolReference in references) {
    if (reference is EvaluatorReference) {
      for (targetElement in reference.targetElements) {
        for (typeElement in typeElements(editor, offset, targetElement)) {
          yield(PsiSymbolService.getInstance().asSymbol(typeElement))
        }
      }
    }
    else {
      for (typeProvider in SymbolTypeProvider.EP_NAME.extensions) {
        for (target in reference.resolveReference()) {
          yieldAll(typeProvider.getSymbolTypes(target))
        }
      }
    }
  }
}

internal fun elementTypeTargets(editor: Editor, offset: Int, targetElements: Collection<PsiElement>): Collection<NavigationTarget> {
  val result = SmartList<NavigationTarget>()
  for (targetElement in targetElements) {
    for (typeElement in typeElements(editor, offset, targetElement)) {
      result.add(PsiElementNavigationTarget(typeElement))
    }
  }
  return result
}

private fun typeElements(editor: Editor, offset: Int, targetElement: PsiElement): Collection<PsiElement> {
  for (provider in TypeDeclarationProvider.EP_NAME.extensionList) {
    val result = if (provider is TypeDeclarationPlaceAwareProvider) {
      provider.getSymbolTypeDeclarations(targetElement, editor, offset)
    }
    else {
      provider.getSymbolTypeDeclarations(targetElement)
    }
    return result?.toList() ?: continue
  }
  return emptyList()
}

private fun Sequence<Symbol>.navigationTargets(project: Project): Sequence<NavigationTarget> {
  return flatMap { typeSymbol ->
    SymbolNavigationService.getInstance().getNavigationTargets(project, typeSymbol)
  }
}

internal fun result(navigationTargets: Collection<NavigationTarget>): GTTDActionResult? {
  return when (navigationTargets.size) {
    0 -> null
    1 -> SingleTarget(navigationTargets.single()::getNavigatable)
    else -> MultipleTargets(navigationTargets.map { navigationTarget ->
      SingleTargetWithPresentation(navigationTarget::getNavigatable, navigationTarget.targetPresentation)
    })
  }
}
