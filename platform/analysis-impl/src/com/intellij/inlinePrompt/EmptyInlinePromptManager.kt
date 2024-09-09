// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inlinePrompt

import com.intellij.openapi.editor.Editor
import javax.swing.Icon

internal class EmptyInlinePromptManager : InlinePromptManager {
  override fun isInlinePromptShown(editor: Editor): Boolean = false

  override fun isInlinePromptCodeGenerating(editor: Editor): Boolean = false

  override fun getBulbIcon(): Icon? = null
}