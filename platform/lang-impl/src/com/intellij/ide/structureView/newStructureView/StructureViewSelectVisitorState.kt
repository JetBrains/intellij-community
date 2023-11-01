// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.psi.PsiElement
import javax.swing.tree.TreePath

class StructureViewSelectVisitorState {

  private var stage: StructureViewSelectVisitorStage = StructureViewSelectVisitorStage.FIRST_PASS
  var bestMatch: TreePath? = null
    private set
  private var bestMatchDepth: Int = 0
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
    if (depth > bestMatchDepth) {
      bestMatch = path
      bestMatchDepth = depth
      isExactMatch = isGoodMatch
    }
  }

  override fun toString() =
    "StructureViewSelectVisitorState(stage=$stage, bestMatch=$bestMatch, bestMatchDepth=$bestMatchDepth, isExactMatch=$isExactMatch)"

}

enum class StructureViewSelectVisitorStage {
  FIRST_PASS,
  FIRST_PASS_WITH_OPTIMIZATION,
  SECOND_PASS,
}
