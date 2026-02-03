// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.util.Side

internal class SimpleDiffChangesHolder {
  /**
   * Subset of [allChangeList], without ranges with uncertain bounds (after some modifications).
   * Sorted by the respected side.
   */
  private val validChangeList: List<MutableList<SimpleDiffChange>> = listOf(
    mutableListOf(),
    mutableListOf()
  )

  /**
   * List with all changed ranges that were found at the previous rediff.
   * It might be outdated if the documents were modified since, or some changes were explicitly hidden.
   *
   * NB: Unsorted if [com.intellij.diff.tools.util.text.TwosideTextDiffProvider.noFitnessForParticularPurposePromised]
   */
  private val allChangeList: MutableList<SimpleDiffChange> = mutableListOf()

  fun getValidChanges(side: Side): List<SimpleDiffChange> = side.selectNotNull(validChangeList).toList()

  fun getAllChanges(): List<SimpleDiffChange> = allChangeList.filter { !it.isDestroyed }

  fun addAll(diffChangeList: List<SimpleDiffChange>) {
    check(validChangeList.all { it.isEmpty() } && allChangeList.isEmpty()) { "Changes lists are not empty!" }
    for (side in Side.entries) {
      val sideValidChanges = side.selectNotNull(validChangeList)
      sideValidChanges.addAll(diffChangeList)
      sideValidChanges.sortBy { it.getStartLine(side) }
    }
    allChangeList.addAll(diffChangeList)
  }

  fun invalidateChanges(diffChangeList: Collection<SimpleDiffChange>) {
    validChangeList.forEach { it.removeAll(diffChangeList) }
    check(validChangeList.first().size == validChangeList.last().size) { "Some change wasn't present in one of the lists" }
  }

  fun clear() {
    validChangeList.forEach { it.clear() }
    allChangeList.clear()
  }

}