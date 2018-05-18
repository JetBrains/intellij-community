/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.components.ServiceManager
import org.jetbrains.concurrency.Promise

abstract class PasswordSafe : PasswordStorage {
  companion object {
    val instance: PasswordSafe
      get() = ServiceManager.getService(PasswordSafe::class.java)
  }

  abstract val isMemoryOnly: Boolean

  abstract operator fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean)

  abstract fun getAsync(attributes: CredentialAttributes): Promise<Credentials>

  abstract fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean
}
