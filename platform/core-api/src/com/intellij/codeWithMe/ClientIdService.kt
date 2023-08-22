// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

@ScheduledForRemoval
@Deprecated("The only purpose of this service is `isValid` method this should also be eliminated ASAP")
interface ClientIdService {
  companion object {
    fun tryGetInstance(): ClientIdService? {
      if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
        return null
      }

      val app = ApplicationManager.getApplication()
      if (app == null || app.isDisposed) {
        return null
      }
      return app.serviceOrNull<ClientIdService>()
    }
  }

  fun isValid(clientId: ClientId?): Boolean
}
