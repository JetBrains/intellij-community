// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.grayText

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class InlineState private constructor(
  var suggestionIndex: Int = 0,
  var lastStartOffset: Int = 0,
  var lastModificationStamp: Long = 0,
  var suggestions: List<GrayTextElement> = emptyList(),
) {
  fun init() {
    suggestionIndex = 0
  }

  fun rest() {
    lastModificationStamp = 0
  }

  companion object {
    private val GRAY_TEXT_STATE: Key<InlineState> = Key.create("gray.text.completion.state")

    fun Editor.getGrayTextState(): InlineState? = getUserData(GRAY_TEXT_STATE)
    fun Editor.initOrGetGrayTextState(): InlineState = getGrayTextState() ?: InlineState().also { putUserData(GRAY_TEXT_STATE, it) }
    fun Editor.resetGrayTextState() = putUserData(GRAY_TEXT_STATE, null)
  }
}
