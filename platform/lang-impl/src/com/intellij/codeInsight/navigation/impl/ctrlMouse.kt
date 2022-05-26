// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.*
import com.intellij.model.psi.impl.DeclarationOrReference
import com.intellij.model.psi.impl.TargetData
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

internal fun TargetData.ctrlMouseInfo(): CtrlMouseInfo? {
  val targets = this.targets
  if (targets.isEmpty()) {
    return null
  }
  val ranges = highlightRanges()
  val singleTarget = targets.singleOrNull()
  return if (singleTarget != null) {
    SingleSymbolCtrlMouseInfo(singleTarget.symbol, elementAtOffset(), ranges, this is TargetData.Declared)
  }
  else {
    MultipleTargetElementsInfo(ranges)
  }
}

internal fun TargetData.ctrlMouseData(project: Project): CtrlMouseData? {
  val targets = this.targets
  if (targets.isEmpty()) {
    return null
  }
  val ranges = highlightRanges()
  val singleTarget = targets.singleOrNull()
  if (singleTarget == null) {
    return multipleTargetsCtrlMouseData(ranges)
  }
  else {
    return symbolCtrlMouseData(project, singleTarget.symbol, elementAtOffset(), ranges, this is TargetData.Declared)
  }
}

internal fun TargetData.elementAtOffset(): PsiElement {
  return when (this) {
    is TargetData.Declared -> {
      declarations.last().declaringElement
    }
    is TargetData.Referenced -> {
      // If there is an evaluator reference in the list, then it will be the last one,
      // otherwise we don't care about the element at offset because it's not used to generate doc for symbol references.
      references.last().element
    }
  }
}

internal fun TargetData.highlightRanges(): List<TextRange> {
  val singleDeclarationOrReference = drs.singleOrNull()
  if (singleDeclarationOrReference != null) {
    return singleDeclarationOrReference.ranges
  }
  val rangeLists = drs.mapTo(HashSet(), DeclarationOrReference::ranges)
  val singleRangeList = rangeLists.singleOrNull()
  if (singleRangeList != null) {
    // In case there are multi-range references, we want to highlight multiple ranges
    // only if the ranges of each reference are equal to the ranges of other references,
    // for example: ref1 has a multi-range of [range1, range2, range3], and ref2 has a multi-range of [range1, range2, range3].
    return singleRangeList
  }
  else {
    // Otherwise, we want to highlight only range with the offset,
    // for example: ref1 has a multi-range of [range1, range2], and ref2 has a multi-range of [range2, range3]
    //
    // References in TargetData$Referenced#references have the same absolute range, so we can choose any reference.
    // Multi-range symbol references are not yet supported => symbol references have only 1 range.
    // There can be at most 1 evaluator reference in the list, its (multi-)range has only 1 common segment with symbol references range.
    return listOf(drs.first().rangeWithOffset)
  }
}
