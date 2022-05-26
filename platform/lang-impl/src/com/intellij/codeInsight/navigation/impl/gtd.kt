// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.TargetData
import com.intellij.model.psi.impl.declaredReferencedData
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun gotoDeclaration(file: PsiFile, offset: Int): GTDActionData? {
  return processInjectionThenHost(file, offset, ::gotoDeclarationInner)
}

/**
 * "Go To Declaration" action data
 */
@ApiStatus.Internal
interface GTDActionData {

  fun ctrlMouseInfo(): CtrlMouseInfo?

  fun ctrlMouseData(): CtrlMouseData?

  fun result(): NavigationActionResult?
}

private fun gotoDeclarationInner(file: PsiFile, offset: Int): GTDActionData? {
  return fromDirectNavigation(file, offset)
         ?: fromTargetData(file, offset)
}

private fun fromTargetData(file: PsiFile, offset: Int): GTDActionData? {
  val (declaredData, referencedData) = declaredReferencedData(file, offset) ?: return null
  val targetData = referencedData   // prefer referenced because GTD follows references first
                   ?: declaredData  // offer navigation between declarations of the declared symbol
                   ?: return null
  return targetData.toGTDActionData(file.project)
}

internal fun TargetData.toGTDActionData(project: Project): GTDActionData {
  return TargetGTDActionData(project, this)
}

private class TargetGTDActionData(private val project: Project, private val targetData: TargetData) : GTDActionData {

  override fun ctrlMouseInfo(): CtrlMouseInfo? = targetData.ctrlMouseInfo()

  override fun ctrlMouseData(): CtrlMouseData? = targetData.ctrlMouseData(project)

  override fun result(): NavigationActionResult? {
    //old behaviour: use gtd target provider if element has only a single target
    targetData.targets.singleOrNull()?.let { (symbol, navigationProvider) ->
      extractSingleTargetResult(symbol, navigationProvider)?.let { result -> return result }
    }

    data class GTDSingleTarget(val navigationTarget: NavigationTarget, val navigationProvider: Any?)

    val result = SmartList<GTDSingleTarget>()
    for ((symbol, navigationProvider) in targetData.targets) {
      for (navigationTarget in SymbolNavigationService.getInstance().getNavigationTargets(project, symbol)) {
        result += GTDSingleTarget(navigationTarget, navigationProvider)
      }
    }
    return when (result.size) {
      0 -> null
      1 -> {
        // don't compute presentation for single target
        val (navigationTarget, navigationProvider) = result.single()
        navigationTarget.navigationRequest()?.let { request ->
          SingleTarget(request, navigationProvider)
        }
      }
      else -> {
        val targets = result.map { (navigationTarget, navigationProvider) ->
          LazyTargetWithPresentation(navigationTarget::navigationRequest, navigationTarget.targetPresentation, navigationProvider)
        }
        MultipleTargets(targets)
      }
    }
  }

  private fun extractSingleTargetResult(symbol: Symbol, navigationProvider: Any?): SingleTarget? {
    val el = PsiSymbolService.getInstance().extractElementFromSymbol(symbol) ?: return null
    val nav = el.gtdTargetNavigatable() ?: return null
    if (nav == el) {
      return null
    }
    return nav.navigationRequest()?.let { request ->
      SingleTarget(request, navigationProvider)
    }
  }
}
