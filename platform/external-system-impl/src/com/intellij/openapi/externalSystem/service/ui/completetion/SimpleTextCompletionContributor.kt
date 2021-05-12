// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completetion

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

@ApiStatus.Experimental
abstract class SimpleTextCompletionContributor : TextCompletionContributor<JTextComponent> {
  abstract fun getCompletionVariants(textToComplete: String): List<String>

  override fun getTextToComplete(owner: JTextComponent): String {
    return owner.text
  }

  override fun getCompletionVariants(owner: JTextComponent, textToComplete: String): Iterable<TextCompletionInfo> {
    return getCompletionVariants(textToComplete).map { TextCompletionInfo(it) }
  }

  override fun whenTextModified(owner: JTextComponent, listener: () -> Unit, parentDisposable: Disposable) {
    val documentListener = object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        listener()
      }
    }
    owner.document.addDocumentListener(documentListener)
    Disposer.register(parentDisposable, Disposable { owner.document.removeDocumentListener(documentListener) })
  }

  companion object {
    fun create(completionVariants: (String) -> List<String>) = object : SimpleTextCompletionContributor() {
      override fun getCompletionVariants(textToComplete: String): List<String> {
        return completionVariants(textToComplete)
      }
    }
  }
}