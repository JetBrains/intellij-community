// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ai

import com.intellij.openapi.editor.Editor

internal class EmptyInlinePromptManager : InlinePromptManager {
  override fun isInlinePromptShown(editor: Editor): Boolean = false
}