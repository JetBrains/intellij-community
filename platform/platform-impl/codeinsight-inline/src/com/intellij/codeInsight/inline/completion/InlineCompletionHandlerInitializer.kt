// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface InlineCompletionHandlerInitializer {

  fun initialize(editor: Editor, scope: CoroutineScope, disposable: Disposable): InlineCompletionHandler?

  companion object {
    private val EP_NAME = ExtensionPointName.create<InlineCompletionHandlerInitializer>(
      "com.intellij.inline.completion.handlerInitializer"
    )

    @ApiStatus.Internal
    fun initialize(editor: Editor, scope: CoroutineScope, disposable: Disposable): InlineCompletionHandler? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.initialize(editor, scope, disposable) }
    }
  }
}
