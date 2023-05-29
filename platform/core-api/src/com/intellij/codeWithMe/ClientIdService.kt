// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

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
      return app.service<ClientIdService>()
    }
  }

  var clientIdValue: String?

  val checkLongActivity: Boolean

  fun isValid(clientId: ClientId?): Boolean

  @Deprecated("Use create a per-client service that implements disposable to get proper disposable associated with the client id")
  fun toDisposable(clientId: ClientId?): Disposable
}
