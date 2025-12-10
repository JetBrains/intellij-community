// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

/**
 * Only the first extension with [shouldUserSettingsBeOverriden]=`true` has effect.
 *
 * If the proxy configuration requires authentication and the credentials are known, the credential store can be populated via
 * [ProxyAuthentication.getInstance].
 */
interface ProxySettingsOverrideProvider {
  /**
   * Should return true if the provider wants to override user's proxy settings. Expected to be immutable.
   */
  val shouldUserSettingsBeOverriden: Boolean

  /**
   * [ProxyConfigurationProvider] which provides the proxy configuration to be used instead of user settings.
   */
  val proxyConfigurationProvider: ProxyConfigurationProvider
}
