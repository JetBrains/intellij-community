// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.components.ServiceManager
import org.jetbrains.concurrency.Promise

abstract class PasswordSafe : PasswordStorage {
  companion object {
    @JvmStatic
    val instance: PasswordSafe
      get() = ServiceManager.getService(PasswordSafe::class.java)
  }

  /**
   * State of "Remember" check box is global. If user did uncheck for one dialog, it should be unchecked for another.
   */
  abstract var isRememberPasswordByDefault: Boolean

  abstract val isMemoryOnly: Boolean

  abstract operator fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean)

  abstract fun getAsync(attributes: CredentialAttributes): Promise<Credentials?>

  abstract fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean
}