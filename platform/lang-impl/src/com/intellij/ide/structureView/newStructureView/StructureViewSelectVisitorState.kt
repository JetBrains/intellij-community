// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.ide.structureView.customRegions.CustomRegionTreeElement
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

@ApiStatus.Internal
class StructureViewSelectVisitorState {

  private var stage: StructureViewSelectVisitorStage = StructureViewSelectVisitorStage.FIRST_PASS
  var bestMatch: TreePath? = null
    private set
  private var bestMatchDepth: Int = 0
  private var bestMatchLength: Int = Integer.MAX_VALUE
  var isExactMatch: Boolean = false
    private set

  fun canUseOptimization(): Boolean = stage != StructureViewSelectVisitorStage.SECOND_PASS

  fun usedOptimization() {
    check(canUseOptimization())
    stage = StructureViewSelectVisitorStage.FIRST_PASS_WITH_OPTIMIZATION
  }

  fun isOptimizationUsed(): Boolean = stage == StructureViewSelectVisitorStage.FIRST_PASS_WITH_OPTIMIZATION

  fun disableOptimization() {
    stage = StructureViewSelectVisitorStage.SECOND_PASS
  }

  fun updateIfBetterMatch(path: TreePath, isGoodMatch: Boolean) {
    val depth = path.pathCount
    val length = StructureViewComponent.unwrapValue(path.lastPathComponent)?.textLength ?: Integer.MAX_VALUE
    if (depth > bestMatchDepth || (depth == bestMatchDepth && length < bestMatchLength)) {
      bestMatch = path
      bestMatchDepth = depth
      bestMatchLength = length
      isExactMatch = isGoodMatch
    }
  }

  override fun toString() =
    "StructureViewSelectVisitorState(stage=$stage, bestMatch=$bestMatch, bestMatchDepth=$bestMatchDepth, isExactMatch=$isExactMatch)"

}

@ApiStatus.Internal
enum class StructureViewSelectVisitorStage {
  FIRST_PASS,
  FIRST_PASS_WITH_OPTIMIZATION,
  SECOND_PASS,
}

private val Any.textLength: Int?
  get() = when (this) {
    is PsiElement -> textLength
    else -> null
  }
