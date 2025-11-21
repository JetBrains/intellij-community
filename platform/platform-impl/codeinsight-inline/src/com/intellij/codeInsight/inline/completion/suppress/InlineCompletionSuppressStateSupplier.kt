// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suppress

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlineCompletionSuppressStateSupplier {

  /**
   * Whether inline completion should not be invoked right now.
   *
   * If the implementation returns `true` while inline completion is already shown, inline completion is NOT hidden.
   */
  fun isSuppressed(editor: Editor): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName.create<InlineCompletionSuppressStateSupplier>("com.intellij.inline.completion.suppress.state.supplier")

    internal fun isSuppressed(editor: Editor): Boolean = EP_NAME.extensionList.any { it.isSuppressed(editor) }
  }
}
