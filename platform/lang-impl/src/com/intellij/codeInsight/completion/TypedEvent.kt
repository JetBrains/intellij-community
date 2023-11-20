// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

internal class TypedEvent(private val charTyped: Char,
                          private val offset: Int,
                          private val phase: TypedHandlerPhase) {
  override fun toString(): String = "charTyped: $charTyped; offset: $offset; phase: $phase"

  enum class TypedHandlerPhase {
    CHECK_AUTO_POPUP,
    AUTO_POPUP,
    BEFORE_SELECTION_REMOVED,
    BEFORE_CHAR_TYPED,
    CHAR_TYPED
  }
}