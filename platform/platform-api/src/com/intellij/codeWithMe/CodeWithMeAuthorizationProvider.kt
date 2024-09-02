// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import org.jetbrains.annotations.ApiStatus

@Deprecated("Not used anymore, com.intellij.platform.ide.provisioner.ProvisionedServiceRegistry is the new one")
@ApiStatus.Internal
@ApiStatus.Experimental
interface CodeWithMeAuthorizationProvider {
  /**
   * Authorization header is requested whenever 401 HTTP code is recieved from Lobby server
   *
   * Returns value of Authorization HTTP header
   */
  fun getAuthorizationHeader(serverUrl: String): String?
}