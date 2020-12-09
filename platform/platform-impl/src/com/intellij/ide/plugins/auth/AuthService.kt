// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.auth

import com.intellij.openapi.extensions.ExtensionPointName
import java.util.stream.Stream

/**
 * Every service used to provide authentication for custom repositories should implement
 * this interface and inform about change authorization status via [com.intellij.ide.plugins.auth.PluginAuthService.setAuthStatus].
 */
interface PluginsAuthExtension {
  val name: String

  /**
   * This method returns true if [url] is supported - service can generate
   * authentication headers for this url else this method returns false.
   *
   * For one url there have to be only one service which returns [isUrlSupported] true.
   */
  fun isUrlSupported(url: String): Boolean

  /**
   * If [url] is supported and authorization status is [AuthStatus.AUTHORIZED] this method
   * should return authentication headers else this method should return `null`.
   */
  fun generateAuthHeaders(url: String): Map<String, String>?

  /**
   * This method should return `true` if the user was authenticated, if not it should init auth process asynchronously
   * and return `false`
   *
   * @param presentableRequestor requestor for auth
   */
  fun initAuthIfNeeded(presentableRequestor: String) : Boolean

  companion object {
    fun getAuthServices(): Stream<PluginsAuthExtension> = EP_NAME.extensions()

    val EP_NAME: ExtensionPointName<PluginsAuthExtension> = ExtensionPointName.create("com.intellij.pluginsAuthExtension")
  }
}
enum class AuthStatus{
  AUTHORIZED, // service can generate not null authentication headers for supported hosts
  NOT_AUTHORIZED // service can't generate not null authentication headers for supported hosts
}
