// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

@Service
@Internal
class ActionCopiedShortcutsTracker {
  private val actionToSourceId = WeakHashMap<AnAction, String>()

  @Synchronized
  fun onActionCopiedFromId(action: AnAction, sourceId: String) {
    actionToSourceId[action] = sourceId
  }

  @Synchronized
  fun getSourceId(action: AnAction): String? = actionToSourceId[action]

  companion object {
    fun getInstance(): ActionCopiedShortcutsTracker = ApplicationManager.getApplication().getService(ActionCopiedShortcutsTracker::class.java)
  }
}