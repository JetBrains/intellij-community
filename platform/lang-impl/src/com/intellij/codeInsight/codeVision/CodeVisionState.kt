// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.util.TextRange

sealed class CodeVisionState(val isReady: Boolean, val result: List<Pair<TextRange, CodeVisionEntry>>) {
  companion object{
    val READY_EMPTY = Ready(emptyList())
  }
  class Ready(lenses: List<Pair<TextRange, CodeVisionEntry>>) : CodeVisionState(true, lenses)
  object NotReady : CodeVisionState(false, emptyList())
}