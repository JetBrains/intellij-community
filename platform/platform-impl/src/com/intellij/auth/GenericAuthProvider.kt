// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.auth

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface GenericAuthProviderExtension {
  val authProviders: List<GenericAuthProvider>
}

@ApiStatus.Internal
@ApiStatus.Experimental
interface GenericAuthProvider {
  companion object {
    val EP_NAME : ExtensionPointName<GenericAuthProviderExtension> = ExtensionPointName("com.intellij.genericAuthProvider")
  }

  /**
   * Should be unique within a [ClientAppSession].
   */
  val id: String

  /**
   * Gives some authentication data for some request.
   * Data format (e.g. json schema in case of json-serialized data) needs to be specified for each [id] separately.
   * For example, implementations for some [id] can just forward the request to a third-party tool.
   * Use [GenericAuthService] to access [GenericAuthProvider] through RD protocol.
   */
  suspend fun getAuthData(request: String): String
}