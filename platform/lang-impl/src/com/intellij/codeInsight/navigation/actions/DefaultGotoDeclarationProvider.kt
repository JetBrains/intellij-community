// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.findReferences
import com.intellij.codeInsight.navigation.GotoDeclarationProvider
import com.intellij.codeInsight.navigation.PsiElementNavigationTarget
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findTargetElementsFromProviders
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.function.Consumer

class DefaultGotoDeclarationProvider : GotoDeclarationProvider {

  override fun collectTargets(project: Project, editor: Editor, file: PsiFile, consumer: Consumer<in NavigationTarget>) {
    val elements = findTargetElementsFromProviders(project, editor, editor.caretModel.offset)
    if (elements != null && elements.isNotEmpty()) {
      for (element in elements) {
        consumer.accept(PsiElementNavigationTarget(element))
      }
      return
    }
    for (reference in findReferences(editor, file)) {
      for (result in reference.resolve(false)) {
        val symbol = result.target
        for (target in getNavigationTargets(project, symbol)) {
          consumer.accept(target)
        }
      }
    }
  }

  private fun getNavigationTargets(project: Project, symbol: Symbol): Collection<NavigationTarget> {
    // TODO obtain targets from reference
    return listOf(getNavigationTarget(project, symbol))
  }

  private fun getNavigationTarget(project: Project, symbol: Symbol): NavigationTarget {
    return when (symbol) {
      is NavigationTarget -> symbol
      is PsiElement -> PsiElementNavigationTarget(symbol)
      is PsiSymbol -> PsiElementNavigationTarget(symbol.element)
      else -> TODO()
    }
  }
}
