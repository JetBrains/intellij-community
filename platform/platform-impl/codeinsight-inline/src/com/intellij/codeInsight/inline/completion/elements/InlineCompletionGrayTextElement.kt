// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils

class InlineCompletionGrayTextElement(
  text: String
) : InlineCompletionColorTextElement(text, InlineCompletionFontUtils::color) {

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this)

  open class Presentable(
    element: InlineCompletionElement
  ) : InlineCompletionColorTextElement.Presentable(element, InlineCompletionFontUtils::color)
}
