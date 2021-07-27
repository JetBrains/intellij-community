// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completion

import org.jetbrains.annotations.ApiStatus
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent

@ApiStatus.Experimental
abstract class JTextCompletionContributor<C : JTextComponent>(completionType: CompletionType) : AbstractTextCompletionContributor<C>() {
  override fun getTextToComplete(owner: C): String {
    val caretPosition = getCaretPosition(owner)
    val wordRange = getWordRange(owner, caretPosition)
    val textToCompleteRange = wordRange.first until caretPosition
    return owner.text.substring(textToCompleteRange)
  }

  private fun getWordRange(owner: C, offset: Int): IntRange {
    var wordStartPosition = 0
    for (word in owner.text.split(" ")) {
      val wordEndPosition = wordStartPosition + word.length
      if (offset in wordStartPosition..wordEndPosition) {
        return wordStartPosition until wordEndPosition
      }
      wordStartPosition = wordEndPosition + 1
    }
    throw BadLocationException(owner.text, offset)
  }

  private fun getCaretPosition(owner: C): Int {
    return maxOf(0, minOf(owner.text.length, owner.caretPosition))
  }

  private fun insert(owner: C, variant: TextCompletionInfo) {
    val textToComplete = getTextToComplete(owner)
    val textCompletionSuffix = variant.text.removePrefix(textToComplete)
    val caretPosition = getCaretPosition(owner)
    owner.document.insertString(caretPosition, textCompletionSuffix, null)
  }

  private fun replace(owner: C, variant: TextCompletionInfo) {
    val caretPosition = getCaretPosition(owner)
    val wordRange = getWordRange(owner, caretPosition)
    owner.document.remove(caretPosition, wordRange.last - caretPosition + 1)
    val textToCompleteRange = wordRange.first until caretPosition
    val textToComplete = owner.text.substring(textToCompleteRange)
    val textCompletionSuffix = variant.text.removePrefix(textToComplete)
    owner.document.insertString(caretPosition, textCompletionSuffix, null)
  }

  init {
    whenVariantChosen { owner, variant ->
      when (completionType) {
        CompletionType.INSERT -> insert(owner, variant)
        CompletionType.REPLACE -> replace(owner, variant)
      }
    }
  }

  enum class CompletionType { INSERT, REPLACE }

  companion object {
    fun <C : JTextComponent> create(
      completionType: CompletionType = CompletionType.INSERT,
      completionVariants: (String) -> List<TextCompletionInfo>
    ) = object : JTextCompletionContributor<C>(
      completionType) {
      override fun getCompletionVariants(owner: C, textToComplete: String): List<TextCompletionInfo> {
        return completionVariants(textToComplete)
      }
    }
  }
}