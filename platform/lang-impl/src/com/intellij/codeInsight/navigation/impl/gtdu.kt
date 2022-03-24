// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.find.actions.PsiTargetVariant
import com.intellij.find.actions.SearchTargetVariant
import com.intellij.find.actions.TargetVariant
import com.intellij.find.usages.impl.symbolSearchTarget
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.TargetData
import com.intellij.model.psi.impl.declaredReferencedData
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList

internal fun gotoDeclarationOrUsages(file: PsiFile, offset: Int): GTDUActionData? {
  return processInjectionThenHost(file, offset, ::gotoDeclarationOrUsagesInner)
}

/**
 * "Go To Declaration Or Usages" action data
 */
internal interface GTDUActionData {

  fun ctrlMouseInfo(): CtrlMouseInfo?

  fun ctrlMouseData(): CtrlMouseData?

  fun result(): GTDUActionResult?
}

/**
 * "Go To Declaration Or Usages" action result
 */
internal sealed class GTDUActionResult {
  /**
   * Go To Declaration
   */
  class GTD(val navigationActionResult: NavigationActionResult) : GTDUActionResult()

  /**
   * Show Usages
   */
  class SU(val targetVariants: List<TargetVariant>) : GTDUActionResult() {

    init {
      require(targetVariants.isNotEmpty())
    }
  }
}

private fun gotoDeclarationOrUsagesInner(file: PsiFile, offset: Int): GTDUActionData? {
  return fromDirectNavigation(file, offset)?.toGTDUActionData()
         ?: fromTargetData(file, offset)
}

private fun fromTargetData(file: PsiFile, offset: Int): GTDUActionData? {
  val (declaredData, referencedData) = declaredReferencedData(file, offset) ?: return null
  return referencedData?.toGTDActionData(file.project)?.toGTDUActionData() // GTD of referenced symbols
         ?: (referencedData)?.let { ShowUsagesGTDUActionData(file.project, it) } // SU of referenced symbols if nowhere to navigate
         ?: declaredData?.let { ShowUsagesGTDUActionData(file.project, it) } // SU of declared symbols
}

internal fun GTDActionData.toGTDUActionData(): GTDUActionData? {
  val gtdActionResult = result() ?: return null                           // nowhere to navigate
  return object : GTDUActionData {
    override fun ctrlMouseInfo(): CtrlMouseInfo? = this@toGTDUActionData.ctrlMouseInfo()
    override fun ctrlMouseData(): CtrlMouseData? = this@toGTDUActionData.ctrlMouseData()
    override fun result(): GTDUActionResult = GTDUActionResult.GTD(gtdActionResult)
  }
}

private class ShowUsagesGTDUActionData(private val project: Project, private val targetData: TargetData) : GTDUActionData {

  override fun ctrlMouseInfo(): CtrlMouseInfo? = targetData.ctrlMouseInfo()

  override fun ctrlMouseData(): CtrlMouseData? = targetData.ctrlMouseData(project)

  override fun result(): GTDUActionResult? = searchTargetVariants(project, targetData).let { targets ->
    if (targets.isEmpty()) {
      null
    }
    else {
      GTDUActionResult.SU(targets)
    }
  }
}

private fun searchTargetVariants(project: Project, data: TargetData): List<TargetVariant> {
  return data.targets.mapNotNullTo(SmartList()) { (symbol, _) ->
    val psi: PsiElement? = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
    if (psi == null) {
      symbolSearchTarget(project, symbol)?.let(::SearchTargetVariant)
    }
    else {
      PsiTargetVariant(psi)
    }
  }
}
