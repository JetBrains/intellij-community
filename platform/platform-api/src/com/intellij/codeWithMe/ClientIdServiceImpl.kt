// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeWithMe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

open class ClientIdServiceImpl : ClientIdService {
  private val storage = ThreadLocal<String>()

  override var clientIdValue: String?
    get() = storage.get()
    set(value) = storage.set(value)

  @ApiStatus.Internal
  override val checkLongActivity = false

  override fun isValid(clientId: ClientId?) = true

  override fun toDisposable(clientId: ClientId?) = ApplicationManager.getApplication() ?: Disposer.newDisposable()
}