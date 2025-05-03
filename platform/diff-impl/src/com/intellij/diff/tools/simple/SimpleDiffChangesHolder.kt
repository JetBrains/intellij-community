// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.util.Side

internal class SimpleDiffChangesHolder {
  private val validChangeList: List<MutableList<SimpleDiffChange>> = listOf(
    mutableListOf(),
    mutableListOf()
  )
  private val allChangeList: MutableList<SimpleDiffChange> = mutableListOf<SimpleDiffChange>()

  fun isEmpty(): Boolean = validChangeList.all { it.isEmpty() }

  fun getValidChanges(side: Side): List<SimpleDiffChange> = side.selectNotNull(validChangeList.first(), validChangeList.last()).toList()

  fun getAllChanges(): List<SimpleDiffChange> = allChangeList.filter { !it.isDestroyed }

  fun addAll(diffChangeList: List<SimpleDiffChange>) {
    check(validChangeList.first().isEmpty() && validChangeList.last().isEmpty() && allChangeList.isEmpty()) { "Changes lists are not empty!" }
    validChangeList.forEachIndexed { index, sideList ->
      sideList.addAll(diffChangeList)
      val side = Side.fromIndex(index)
      sideList.sortBy { it.getStartLine(side) }
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