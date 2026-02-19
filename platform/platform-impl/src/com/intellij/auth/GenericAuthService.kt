// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.auth

import com.intellij.openapi.client.ClientSession
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface GenericAuthService {
  companion object {
    fun getInstance(session: ClientSession): GenericAuthService = session.service()
  }
  suspend fun getAuthData(providerId: String, request: String): String?
}