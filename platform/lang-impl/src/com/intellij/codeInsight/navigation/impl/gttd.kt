// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.SymbolTypeProvider
import com.intellij.codeInsight.navigation.actions.TypeDeclarationPlaceAwareProvider
import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.codeInsight.navigation.multipleTargetsCtrlMouseData
import com.intellij.codeInsight.navigation.symbolCtrlMouseData
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.EvaluatorReference
import com.intellij.model.psi.impl.TargetData
import com.intellij.model.psi.impl.declaredReferencedData
import com.intellij.model.psi.impl.mockEditor
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList

internal fun gotoTypeDeclaration(file: PsiFile, offset: Int): GTTDActionData? {
  return processInjectionThenHost(file, offset, ::gotoTypeDeclarationInner)
}

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

  private fun typeSymbols() = targetData.typeSymbols(project, editor, offset)

  fun ctrlMouseData(): CtrlMouseData? {
    val typeSymbols = typeSymbols().take(2).toList()
    return when (typeSymbols.size) {
      0 -> null
      1 -> symbolCtrlMouseData(
        project,
        typeSymbols.single(),
        targetData.elementAtOffset(),
        targetData.highlightRanges(),
        declared = false,
      )
      else -> multipleTargetsCtrlMouseData(targetData.highlightRanges())
    }
  }

  fun result(): NavigationActionResult? {
    return result(typeSymbols().navigationTargets(project).toCollection(LinkedHashSet()))
  }
}

private fun TargetData.typeSymbols(project: Project, editor: Editor, offset: Int): Sequence<Symbol> {
  return when (this) {
    is TargetData.Declared -> typeSymbols(project, editor, offset)
    is TargetData.Referenced -> typeSymbols(project, editor, offset)
  }
}

private fun TargetData.Declared.typeSymbols(project: Project, editor: Editor, offset: Int): Sequence<Symbol> = sequence {
  val psiSymbolService = PsiSymbolService.getInstance()
  for (declaration: PsiSymbolDeclaration in declarations) {
    val target = declaration.symbol
    val psi = psiSymbolService.extractElementFromSymbol(target)
    if (psi != null) {
      for (typeElement in typeElements(editor, offset, psi)) {
        yield(psiSymbolService.asSymbol(typeElement))
      }
    }
    else {
      for (typeProvider in SymbolTypeProvider.EP_NAME.extensions) {
        yieldAll(typeProvider.getSymbolTypes(project, target))
      }
    }
  }
}

private fun TargetData.Referenced.typeSymbols(project: Project, editor: Editor, offset: Int): Sequence<Symbol> = sequence {
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
          yieldAll(typeProvider.getSymbolTypes(project, target))
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

internal fun result(navigationTargets: Collection<NavigationTarget>): NavigationActionResult? {
  return when (navigationTargets.size) {
    0 -> null
    1 -> SingleTarget(navigationTargets.single()::navigationRequest, null)
    else -> MultipleTargets(navigationTargets.map { navigationTarget ->
      LazyTargetWithPresentation(navigationTarget::navigationRequest, navigationTarget.computePresentation(), null)
    })
  }
}
