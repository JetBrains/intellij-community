// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus.Internal

@Service
@Internal
class ActionCopiedShortcutsTracker {
  private val actionToSourceId = CollectionFactory.createConcurrentWeakIdentityMap<AnAction, String>()

  fun onActionCopiedFromId(action: AnAction, sourceId: String) {
    actionToSourceId[action] = sourceId
  }

  fun getSourceId(action: AnAction): String? = actionToSourceId[action]

  companion object {
    fun getInstance(): ActionCopiedShortcutsTracker = ApplicationManager.getApplication().getService(ActionCopiedShortcutsTracker::class.java)
  }
}