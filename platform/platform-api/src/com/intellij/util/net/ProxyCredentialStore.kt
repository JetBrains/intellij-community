// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.util.net.internal.asProxyCredentialStore

/**
 * Implementations may invoke IO and must provide thread-safety guarantees.
 */
interface ProxyCredentialStore {
  companion object {
    @JvmStatic
    fun getInstance(): ProxyCredentialStore = platformCredentialStore

    @Suppress("DEPRECATION", "removal")
    private val platformCredentialStore = (HttpConfigurable::getInstance).asProxyCredentialStore()
  }

  /**
   * Retrieves known credentials for the proxy located at the specified host and port
   */
  fun getCredentials(host: String, port: Int): Credentials?

  /**
   * Stores credentials for the proxy located at the specified host and port.
   * @param credentials null to clear the associated credentials from the persistence
   * @param remember whether to persist the credentials to be reused in future sessions. Has no effect if [credentials] is null
   */
  fun setCredentials(host: String, port: Int, credentials: Credentials?, remember: Boolean)

  /**
   * @return true if credentials for specified proxy location exist and are remembered (not session-only).
   */
  fun areCredentialsRemembered(host: String, port: Int): Boolean

  /**
   * Clears known credentials which are said to be not remembered
   */
  fun clearTransientCredentials()

  /**
   * Clears all known credentials, including the ones that were said to be remembered
   */
  fun clearAllCredentials()
}

fun interface ProxyCredentialProvider {
  /**
   * Retrieves known credentials for the proxy located at the specified host and port
   */
  fun getCredentials(host: String, port: Int): Credentials?
}

fun ProxyCredentialStore.asProxyCredentialProvider(): ProxyCredentialProvider {
  return this as? ProxyCredentialProvider ?: ProxyCredentialProvider(this::getCredentials)
}