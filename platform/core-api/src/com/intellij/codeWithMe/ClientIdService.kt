// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager

interface ClientIdService {
  companion object {
    fun tryGetInstance(): ClientIdService? {
      if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred || ApplicationManager.getApplication().isDisposed) {
        return null
      }
      return ApplicationManager.getApplication().getService(ClientIdService::class.java)
    }
  }

  val clientIdValue: String?

  val checkLongActivity: Boolean

  fun updateClientId(value: String?): AutoCloseable

  fun isValid(clientId: ClientId?): Boolean

  @Deprecated("Use create a per-client service that implements disposable to get proper disposable associated with the client id")
  fun toDisposable(clientId: ClientId?): Disposable
}
