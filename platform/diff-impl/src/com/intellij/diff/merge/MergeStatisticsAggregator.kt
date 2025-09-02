// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class MergeStatisticsAggregator(
  val changes: Int,
  val autoResolvable: Int,
  val autoResolvableWithSemantics : Int,
  val conflicts: Int,
  val language: Language?
) {
  var unresolved: Int = -1
  val initialTimestamp: Long = System.currentTimeMillis()

  private val edited = mutableSetOf<Int>()
  private val resolvedByAiChanges = mutableSetOf<Int>()
  private val rolledBackAfterAI = mutableSetOf<Int>()
  private val undoneAfterAi = mutableSetOf<Int>()
  private val editedAfterAi = mutableSetOf<Int>()

  fun edited(): Int = edited.size
  fun resolvedByAi(): Int = resolvedByAiChanges.size
  fun rolledBackAfterAI(): Int = rolledBackAfterAI.size
  fun undoneAfterAI(): Int = undoneAfterAi.size
  fun editedAfterAI(): Int = editedAfterAi.size

  fun wasEdited(index: Int) {
    edited.add(index)
  }

  fun wasResolvedByAi(index: Int) {
    resolvedByAiChanges.add(index)
  }

  fun wasRolledBackAfterAI(index: Int) {
    resolvedByAiChanges.remove(index)
    rolledBackAfterAI.add(index)
  }

  fun wasUndoneAfterAI(index: Int) {
    resolvedByAiChanges.remove(index)
    undoneAfterAi.add(index)
  }

  fun wasEditedAfterAi(index: Int) {
    editedAfterAi.add(index)
  }
}