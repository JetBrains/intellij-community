// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.openapi.application.ApplicationManager

/**
 * Provides information about credential stores in the current environment.
 */
interface CredentialStoreManager {
  companion object {
    @JvmStatic
    fun getInstance(): CredentialStoreManager = ApplicationManager.getApplication().getService(CredentialStoreManager::class.java)
  }

  /**
   * Checks if specified credential store supported in this environment or not
   */
  fun isSupported(provider: ProviderType): Boolean

  /**
   * Lists available in the current environment credential store providers.
   * For example, in headless Linux it is challenging or even impossible to properly configure usage of 'gnome-keychain',
   * because it requires X11 (via 'libsecret'), so it will be better to exclude [ProviderType.KEYCHAIN] from this list.
   *
   * @return list of supported providers
   */
  fun availableProviders(): List<ProviderType>

  /**
   * Returns default credential store provider.
   * In most cases, it is [ProviderType.KEYCHAIN], but in headless Linux environments it could be something else.
   *
   * @return default credential store provider
   */
  fun defaultProvider(): ProviderType
}
