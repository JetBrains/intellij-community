// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

sealed interface InlineCompletionInsertPolicy {
  val caretShift: Int

  data class Append(val text: String, override val caretShift: Int = text.length) : InlineCompletionInsertPolicy

  data class Skip(val length: Int) : InlineCompletionInsertPolicy {
    override val caretShift: Int
      get() = length
  }
}