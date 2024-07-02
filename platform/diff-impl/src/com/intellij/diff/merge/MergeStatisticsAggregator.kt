// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class MergeStatisticsAggregator(
  val changes: Int,
  val autoResolvable: Int,
  val conflicts: Int,
) {
  private val edited = mutableSetOf<Int>()

  fun edited(): Int = edited.size

  fun wasEdited(index: Int) {
    edited.add(index)
  }

  var unresolved: Int = -1
  val initialTimestamp: Long = System.currentTimeMillis()
}