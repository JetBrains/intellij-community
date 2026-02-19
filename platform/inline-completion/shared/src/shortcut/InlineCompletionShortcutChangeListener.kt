// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
abstract class InlineCompletionShortcutChangeListener : KeymapManagerListener {

  protected abstract fun insertShortcutChanged()

  final override fun shortcutsChanged(keymap: Keymap, actionIds: @NonNls MutableCollection<String>, fromSettings: Boolean) {
    val expectedActionId = IdeActions.ACTION_INSERT_INLINE_COMPLETION
    if (actionIds.contains(expectedActionId) && keymap.getShortcuts(expectedActionId).isNotEmpty()) {
      insertShortcutChanged()
    }
  }

  companion object {
    fun whenInsertShortcutChanged(disposable: Disposable, handler: () -> Unit) {
      val listener = object : InlineCompletionShortcutChangeListener() {
        override fun insertShortcutChanged() = handler()
      }
      application.messageBus.connect(disposable).subscribe(KeymapManagerListener.TOPIC, listener)
    }
  }
}
