// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface DoNotAskManager {
  fun isDoNotAsk(notificationId: String): Boolean

  fun markDoNotAsk(notificationId: String, @Nls displayName: String?)

  fun clearDoNotAsk(notificationId: String)

  fun getDoNotAskNotifications(): Map<String, String>
}

@ApiStatus.Internal
interface DoNotAskAppManager : DoNotAskManager {
  companion object {
    @JvmStatic
    fun getInstance(): DoNotAskAppManager = ApplicationManager.getApplication().getService(DoNotAskAppManager::class.java)
  }
}

@ApiStatus.Internal
interface DoNotAskProjectManager : DoNotAskManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): DoNotAskProjectManager = project.getService(DoNotAskProjectManager::class.java)
  }
}
