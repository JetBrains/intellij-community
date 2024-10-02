// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.auth

import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
class LocalGenericAuthService : GenericAuthService {
  override suspend fun getAuthData(providerId: String, request: String): String {
    val relevantProviders = GenericAuthProvider.EP_NAME.extensionList.flatMap { it.authProviders }.filter { it.id == providerId }
    when (relevantProviders.size) {
      0 -> throw IllegalStateException("No provider with id '$providerId'")
      1 -> return relevantProviders.single().getAuthData(request)
      else -> {
        LOG.error { "Multiple providers with id '$providerId'. Using one of them." }
        return relevantProviders.single().getAuthData(request)
      }
    }
  }
}

private val LOG = getLogger<LocalGenericAuthService>()