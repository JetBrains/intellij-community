// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.codeInsight.navigation.MultipleTargetElementsInfo
import com.intellij.codeInsight.navigation.SingleTargetElementInfo
import com.intellij.model.psi.impl.PsiOrigin
import com.intellij.model.psi.impl.TargetData
import com.intellij.psi.PsiElement

internal fun TargetData.ctrlMouseInfo(): CtrlMouseInfo? {
  return when (this) {
    is TargetData.Declared -> {
      DeclarationCtrlMouseInfo(declaration)
    }
    is TargetData.Referenced -> {
      val ranges = listOf(references.first().absoluteRange)
      val targets = this@ctrlMouseInfo.targets
      if (targets.isEmpty()) {
        return null
      }
      val singleTarget = targets.singleOrNull()
      if (singleTarget != null) {
        SingleSymbolCtrlMouseInfo(singleTarget.symbol, ranges)
      }
      else {
        MultipleTargetElementsInfo(ranges)
      }
    }
    is TargetData.Evaluator -> {
      ctrlMouseInfo(origin, targetElements)
    }
  }
}

private fun ctrlMouseInfo(origin: PsiOrigin, targetElements: Collection<PsiElement>): CtrlMouseInfo {
  require(targetElements.isNotEmpty())
  val singleTargetElement = targetElements.singleOrNull()
  return if (singleTargetElement != null) {
    SingleTargetElementInfo(origin.absoluteRanges, origin.elementAtPointer, singleTargetElement)
  }
  else {
    MultipleTargetElementsInfo(origin.absoluteRanges)
  }
}
