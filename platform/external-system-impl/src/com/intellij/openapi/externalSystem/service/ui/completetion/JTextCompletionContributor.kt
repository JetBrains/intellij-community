// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completetion

import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.whenTextModified
import org.jetbrains.annotations.ApiStatus
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent

@ApiStatus.Experimental
abstract class JTextCompletionContributor : AbstractTextCompletionContributor<JTextComponent>() {
  abstract fun getWordCompletionVariants(owner: JTextComponent, textToComplete: String): Iterable<TextCompletionInfo>

  override fun getTextToComplete(owner: JTextComponent): String {
    val textToCompleteRange = getTextToCompleteRange(owner)
    return owner.text.substring(textToCompleteRange)
  }

  override fun getCompletionVariants(owner: JTextComponent, textToComplete: String): Iterable<TextCompletionInfo> {
    return getWordCompletionVariants(owner, textToComplete)
  }

  private fun getTextToCompleteRange(owner: JTextComponent): IntRange {
    val caretPosition = getCaretPosition(owner)
    var wordStartPosition = 0
    for (word in owner.text.split(" ")) {
      val wordEndPosition = wordStartPosition + word.length
      if (caretPosition in wordStartPosition..wordEndPosition) {
        return wordStartPosition until caretPosition
      }
      wordStartPosition = wordEndPosition + 1
    }
    throw BadLocationException(owner.text, caretPosition)
  }

  private fun getCaretPosition(owner: JTextComponent): Int {
    return maxOf(0, minOf(owner.text.length, owner.caretPosition))
  }

  init {
    whenVariantChosen { owner, variant ->
      val textToComplete = getTextToComplete(owner)
      val textCompletionSuffix = variant.text.removePrefix(textToComplete)
      val caretPosition = getCaretPosition(owner)
      owner.document.insertString(caretPosition, textCompletionSuffix, null)
    }
  }

  companion object {
    fun create(completionVariants: (String) -> List<TextCompletionInfo>) = object : JTextCompletionContributor() {
      override fun getWordCompletionVariants(owner: JTextComponent, textToComplete: String): List<TextCompletionInfo> {
        return completionVariants(textToComplete)
      }
    }
  }
}