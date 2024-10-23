// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface InlineCompletionShortcutHintListener {

  fun choiceShown()

  fun choiceMade()

  fun shouldShowChoice(): Boolean

  fun shouldHideHint(editor: Editor): Boolean

  fun isHintDisabled(): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName.create<InlineCompletionShortcutHintListener>(
      "com.intellij.inline.completion.shortcutHintListener"
    )

    fun choiceShown() {
      EP_NAME.forEachExtensionSafe { it.choiceShown() }
    }

    fun choiceMade() {
      EP_NAME.forEachExtensionSafe { it.choiceMade() }
    }

    fun shouldShowChoice(): Boolean {
      return EP_NAME.extensionList.any { it.shouldShowChoice() }
    }

    fun shouldHideHint(editor: Editor): Boolean {
      return EP_NAME.extensionList.any { it.shouldHideHint(editor) }
    }

    fun isHintDisabled(): Boolean {
      return EP_NAME.extensionList.any { it.isHintDisabled() }
    }
  }
}
