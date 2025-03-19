// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface InlineCompletionInstallListener {
  companion object {
    val TOPIC = Topic.create("com.intellij.codeInsight.inline.completion.InlineCompletionInstallListener", InlineCompletionInstallListener::class.java)
  }

  /**
   * Fired after handler was installed and other listeners were added
   */
  fun handlerInstalled(editor: Editor, handler: InlineCompletionHandler) = Unit

  fun handlerUninstalled(editor: Editor, handler: InlineCompletionHandler) = Unit
}