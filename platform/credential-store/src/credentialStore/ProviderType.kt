// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

enum class ProviderType {
  MEMORY_ONLY, KEYCHAIN, KEEPASS,

  REMOTE,

  // unused, but can't be removed because might be stored in the config, and we must correctly deserialize it
  @Deprecated("Use MEMORY_ONLY")
  DO_NOT_STORE
}
