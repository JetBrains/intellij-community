// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.auth

import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
class LocalGenericAuthService : GenericAuthService {
  override suspend fun getAuthData(providerId: String, request: String): String? {
    val relevantProviders = GenericAuthProvider.EP_NAME.extensionList.flatMap { it.authProviders }.filter { it.id == providerId }
    if (relevantProviders.isEmpty()) {
      LOG.error { "No provider with id '$providerId'. Returning null." }
      return null
    }
    if (relevantProviders.size > 1) {
      LOG.error { "Multiple providers with id '$providerId'. Using one of them." }
    }
    return try {
      relevantProviders.first().getAuthData(request)
    } catch (e: Throwable) {
      LOG.error("Provider with id '$providerId' threw an exception. Returning null.", e)
      null
    }
  }
}

private val LOG = getLogger<LocalGenericAuthService>()