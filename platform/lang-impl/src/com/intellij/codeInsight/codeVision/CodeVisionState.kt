// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.util.TextRange

sealed class CodeVisionState(val isReady: Boolean, val result: List<Pair<TextRange, CodeVisionEntry>>) {
  companion object{
    val READY_EMPTY: Ready = Ready(emptyList())
  }
  class Ready(lenses: List<Pair<TextRange, CodeVisionEntry>>) : CodeVisionState(true, lenses)

  /** If a [CodeVisionProvider.computeCodeVision] returns [NotReady],
   *  it is expected that an appropriate call to [CodeVisionHost.invalidateProvider] is eventually made when necessary data becomes ready
   *  to signalize to the platform that [CodeVisionProvider.computeCodeVision] should be attempted again.
   *
   *  Otherwise, it is not guaranteed that [CodeVisionProvider.computeCodeVision] will be called again,
   *  and particular code vision may be stuck indefinitely. */
  object NotReady : CodeVisionState(false, emptyList())
}