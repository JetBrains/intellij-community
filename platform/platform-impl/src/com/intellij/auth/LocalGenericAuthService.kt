// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.auth

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
class LocalGenericAuthService : GenericAuthService {
  override suspend fun getAuthData(providerId: String, request: String): String {
    val authProviderEP = GenericAuthProvider.EP_NAME.findFirstSafe { it.id == providerId } ?: error("No provider with id '$providerId'")
    val authProvider = authProviderEP.instance
    return authProvider.getAuthData(request)
  }
}