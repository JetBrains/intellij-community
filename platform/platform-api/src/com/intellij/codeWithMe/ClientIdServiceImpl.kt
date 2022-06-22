// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

open class ClientIdServiceImpl : ClientIdService {
  private val storage = ThreadLocal<String>()

  override val clientIdValue: String?
    get() = storage.get()

  override fun updateClientId(value: String?): AutoCloseable {
    val oldValue = storage.get()
    storage.set(value)
    return AutoCloseable { storage.set(oldValue) }
  }

  @ApiStatus.Internal
  override val checkLongActivity = false

  override fun isValid(clientId: ClientId?) = true

  @Suppress("OverridingDeprecatedMember")
  override fun toDisposable(clientId: ClientId?) = ApplicationManager.getApplication() ?: Disposer.newDisposable()
}