// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class CredentialStoreManagerImpl : CredentialStoreManager {
  override fun isSupported(provider: ProviderType): Boolean =
    if (provider === ProviderType.KEYCHAIN) {
      (SystemInfo.isLinux || SystemInfo.isMac) && provider in availableProviders()
    }
    else {
      provider in availableProviders()
    }

  override fun availableProviders(): List<ProviderType> = listOf(ProviderType.MEMORY_ONLY, ProviderType.KEYCHAIN, ProviderType.KEEPASS)

  override fun defaultProvider(): ProviderType = if (SystemInfo.isWindows) ProviderType.KEEPASS else ProviderType.KEYCHAIN
}
