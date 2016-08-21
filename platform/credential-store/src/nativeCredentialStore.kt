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
package com.intellij.credentialStore

import com.intellij.credentialStore.linux.SecretCredentialStore
import com.intellij.credentialStore.macOs.KeyChainCredentialStore
import com.intellij.credentialStore.macOs.isMacOsCredentialStoreSupported
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.util.containers.ContainerUtil

private val nullPassword = Credentials("\u0000", "\u0000")

private class CredentialStoreWrapper(private val store: CredentialStore) : PasswordStorage {
  private val fallbackStore = lazy { FileCredentialStore(memoryOnly = true) }

  private val queueProcessor = QueueProcessor<() -> Unit>({
                                                            it()
                                                          })

  private val postponedCredentials = ContainerUtil.newConcurrentMap<CredentialAttributes, Credentials>()

  @Suppress("OverridingDeprecatedMember")
  override fun getPassword(requestor: Class<*>, accountName: String): String? {
    @Suppress("DEPRECATION")
    val value = super.getPassword(requestor, accountName)
    if (value == null) {
      LOG.catchAndLog {
        // try old key - as hash
        val oldKey = toOldKey(requestor, accountName)
        store.get(oldKey)?.let {
          LOG.catchAndLog { store.set(oldKey, null) }
          set(CredentialAttributes(requestor, accountName), it)
          return it.password
        }
      }
    }
    return value
  }

  override fun get(attributes: CredentialAttributes): Credentials? {
    postponedCredentials.get(attributes)?.let {
      return if (it == nullPassword) null else it
    }

    var store = if (fallbackStore.isInitialized()) fallbackStore.value else store

    try {
      return store.get(attributes)
    }
    catch (e: UnsatisfiedLinkError) {
      store = fallbackStore.value
      LOG.error(e)
      return store.get(attributes)
    }
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    LOG.catchAndLog {
      val store = if (fallbackStore.isInitialized()) fallbackStore.value else store
      if (fallbackStore.isInitialized()) {
        store.set(attributes, credentials)
      }
      else {
        postponedCredentials.put(attributes, credentials ?: nullPassword)
        queueProcessor.add {
          if (!fallbackStore.isInitialized()) {
            LOG.catchAndLog {
              store.set(attributes, credentials)
              postponedCredentials.remove(attributes)
              return@add
            }
          }
          fallbackStore.value.set(attributes, credentials)
          postponedCredentials.remove(attributes)
        }
      }
    }
  }
}

private class MacOsCredentialStoreFactory : CredentialStoreFactory {
  override fun create(): PasswordStorage? {
    if (isMacOsCredentialStoreSupported && SystemProperties.getBooleanProperty("use.mac.keychain", true)) {
      return CredentialStoreWrapper(KeyChainCredentialStore())
    }
    return null
  }
}

private class LinuxSecretCredentialStoreFactory : CredentialStoreFactory {
  override fun create(): PasswordStorage? {
    if (SystemInfo.isLinux && SystemProperties.getBooleanProperty("use.linux.keychain", true)) {
      return CredentialStoreWrapper(SecretCredentialStore("com.intellij.credentialStore.Credential"))
    }
    return null
  }
}