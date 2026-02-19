// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.editorIdOrNull
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlineCompletionRemDevUtils {

  fun isRhizomeUsed(): Boolean {
    return Registry.`is`("inline.completion.rem.dev.use.rhizome", false)
  }

  fun isSupported(editor: Editor): Boolean {
    return editor.editorIdOrNull() != null
  }
}
