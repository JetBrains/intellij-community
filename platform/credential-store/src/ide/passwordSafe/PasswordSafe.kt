// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.passwordSafe

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.application.ApplicationManager

/**
 * [See the documentation](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html).
 */
@Suppress("DEPRECATION", "removal")
abstract class PasswordSafe : CredentialStore, PasswordStorage {
  companion object {
    @JvmStatic
    val instance: PasswordSafe
      get() = ApplicationManager.getApplication().getService(PasswordSafe::class.java)
  }

  /**
   * The state of the "Remember" check box is global; if a user unchecks it in one dialog, it should be unchecked in another.
   */
  abstract var isRememberPasswordByDefault: Boolean

  abstract val isMemoryOnly: Boolean

  abstract operator fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean)

  abstract fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean
}
