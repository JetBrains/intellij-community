// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal

sealed interface WarmupStatus {
  companion object {
    private val key = Key<WarmupStatus>("intellij.warmup.status")

    fun isWarmupInProgress(): Boolean {
      return currentStatus(ApplicationManager.getApplication()) == InProgress
    }

    fun currentStatus(app: Application): WarmupStatus {
      return app.getUserData(key) ?: NotStarted
    }

    @Internal
    fun statusChanged(app: Application, newStatus: WarmupStatus) {
      app.putUserData(key, newStatus)
    }
  }

  object NotStarted: WarmupStatus
  object InProgress: WarmupStatus
  data class Finished(val indexedFileCount: Int): WarmupStatus
}