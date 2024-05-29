// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.application.AccessToken
import org.jetbrains.annotations.ApiStatus
import java.net.Authenticator
import java.net.ProxySelector

/**
 * Provides functionality to customize the behavior of the default [JdkProxyProvider]. Use of this class should be avoided if possible.
 *
 * One should not rely on any ordering guarantees of [customizeProxySelector] or [customizeAuthenticator].
 *
 * Applied customizations take effect before the original [JdkProxyProvider.proxySelector] and [JdkProxyProvider.authenticator].
 *
 * @see [ProxyAuthentication]
 */
interface JdkProxyCustomizer {
  companion object {
    @JvmStatic
    fun getInstance(): JdkProxyCustomizer = CustomizedPlatformJdkProxyProvider
  }

  /**
   * Returns the main [ProxySelector] from [JdkProxyProvider] without customizations applied, i.e., the one that is configured solely by user settings.
   * Shouldn't be used unless absolutely necessary.
   */
  @get:ApiStatus.Experimental
  val originalProxySelector: ProxySelector

  /**
   * Returns the main [Authenticator] from [JdkProxyProvider] without customizations applied, i.e., the one that is configured solely by user settings.
   * Shouldn't be used unless absolutely necessary.
   */
  @get:ApiStatus.Experimental
  val originalAuthenticator: Authenticator

  /**
   * @param proxySelector [ProxySelector.select] should return an _empty_ list in case this [proxySelector] is not applicable.
   */
  fun customizeProxySelector(proxySelector: ProxySelector): AccessToken

  /**
   * @param authenticator [Authenticator.getPasswordAuthentication] should return non-null credentials if it is applicable.
   */
  fun customizeAuthenticator(authenticator: Authenticator): AccessToken
}