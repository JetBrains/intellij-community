// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.TargetData
import com.intellij.model.psi.impl.declaredReferencedData
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.SymbolNavigationService
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
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

  fun result(): GTDActionResult?
}

/**
 * "Go To Declaration" action result
 */
@ApiStatus.Internal
sealed class GTDActionResult {

  /**
   * Single [Navigatable].
   *
   * Might be obtained from direct navigation, in this case requiring [TargetPresentation] doesn't make sense.
   */
  class SingleTarget(val navigatable: () -> Navigatable, val navigationProvider: Any?) : GTDActionResult() {
    constructor(
      navigatable: Navigatable,
      navigationProvider: Any?
    ) : this({ navigatable }, navigationProvider)
  }

  class MultipleTargets(val targets: List<GTDTarget>) : GTDActionResult() {
    init {
      require(targets.isNotEmpty())
    }
  }
}

@ApiStatus.Internal
data class GTDTarget(val navigatable: () -> Navigatable, val presentation: TargetPresentation, val navigationProvider: Any?) {
  constructor(
    navigatable: Navigatable,
    presentation: TargetPresentation,
    navigationProvider: Any?
  ) : this({ navigatable }, presentation, navigationProvider)
}

private fun gotoDeclarationInner(file: PsiFile, offset: Int): GTDActionData? {
  return fromDirectNavigation(file, offset)
         ?: fromTargetData(file, offset)
}

private fun fromTargetData(file: PsiFile, offset: Int): GTDActionData? {
  val (declaredData, referencedData) = declaredReferencedData(file, offset)
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

  override fun result(): GTDActionResult? {
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
        GTDActionResult.SingleTarget(navigationTarget::getNavigatable, navigationProvider)
      }
      else -> {
        val targets = result.map { (navigationTarget, navigationProvider) ->
          GTDTarget(navigationTarget::getNavigatable, navigationTarget.targetPresentation, navigationProvider)
        }
        GTDActionResult.MultipleTargets(targets)
      }
    }
  }

  private fun extractSingleTargetResult(symbol: Symbol, navigationProvider: Any?): GTDActionResult.SingleTarget? {
    val el = PsiSymbolService.getInstance().extractElementFromSymbol(symbol) ?: return null
    val nav = gtdTargetNavigatable(el)
    return if (nav == el) null else GTDActionResult.SingleTarget(nav, navigationProvider)
  }
}
