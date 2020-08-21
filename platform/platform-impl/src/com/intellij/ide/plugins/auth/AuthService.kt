// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.auth


enum class AuthStatus{
  AUTHORIZED, // service can generate not null authentication headers for supported hosts
  NOT_AUTHORIZED // service can't generate not null authentication headers for supported hosts
}

/**
 * Every service used to provide authentication custom repositories should extend
 * this class and inform about change authorization status via [setAuthStatus].
 *
 * @param name name of authorization service, for example, `Google auth`, `JetBrains auth` etc.
 * @param status current authorization status.
 */
abstract class AuthService(val name: String, val status: AuthStatus) {
  init {
    PluginAuthService.register(this)
    //PluginAuthService.setAuthStatus(this, status)
  }

  fun setAuthStatus(status: AuthStatus) {
    PluginAuthService.setAuthStatus(this, status)
  }

  /**
   * This method returns true if [url] is supported - service can generate
   * authentication headers for this url else this method returns false.
   *
   * For one url there have to be only one service which returns [isUrlSupported] true.
   */
  abstract fun isUrlSupported(url: String): Boolean

  /**
   * If [url] is supported and authorization status is [AuthStatus.AUTHORIZED] this method
   * should return authentication headers else this method should return `null`.
   */
  abstract fun generateAuthHeaders(url: String): Map<String, String>?
}