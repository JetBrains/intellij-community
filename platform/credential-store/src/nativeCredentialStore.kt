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

private const val nullPassword = "\u0000"

private class CredentialStoreWrapper(private val store: CredentialStore) : PasswordStorage {
  private val fallbackStore = lazy { FileCredentialStore(memoryOnly = true) }

  private val queueProcessor = QueueProcessor<() -> Unit>({
                                                            it()
                                                          })

  private val postponedCredentials = ContainerUtil.newConcurrentMap<String, String>()

  override fun getPassword(requestor: Class<*>?, key: String): String? {
    val rawKey = getRawKey(key, requestor)

    postponedCredentials.get(rawKey)?.let {
      return if (it == nullPassword) null else it
    }

    var store = if (fallbackStore.isInitialized()) fallbackStore.value else store

    // try old key - as hash
    @Suppress("CanBeVal")
    var value: String?
    try {
      value = store.get(rawKey)
    }
    catch (e: UnsatisfiedLinkError) {
      store = fallbackStore.value
      LOG.error(e)
      value = store.get(rawKey)
    }
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }

    if (value == null) {
      LOG.catchAndLog {
        val oldKey = toOldKey(rawKey)
        value = store.get(oldKey)
        if (value != null) {
          LOG.catchAndLog { store.set(oldKey, null) }
          store.set(key, value!!.toByteArray())
        }
      }
    }
    return value
  }

  override fun setPassword(requestor: Class<*>?, key: String, value: String?) {
    LOG.catchAndLog {
      val store = if (fallbackStore.isInitialized()) fallbackStore.value else store
      val rawKey = getRawKey(key, requestor)
      val passwordData = value?.toByteArray()
      if (fallbackStore.isInitialized()) {
        store.set(rawKey, passwordData)
      }
      else {
        postponedCredentials.put(rawKey, value ?: nullPassword)
        queueProcessor.add {
          if (!fallbackStore.isInitialized()) {
            LOG.catchAndLog {
              store.set(rawKey, passwordData)
              postponedCredentials.remove(rawKey)
              return@add
            }
          }
          fallbackStore.value.set(rawKey, passwordData)
          postponedCredentials.remove(rawKey)
        }
      }
    }
  }
}

private class MacOsCredentialStoreFactory : CredentialStoreFactory {
  override fun create(): PasswordStorage? {
    if (isMacOsCredentialStoreSupported && SystemProperties.getBooleanProperty("use.mac.keychain", true)) {
      return CredentialStoreWrapper(KeyChainCredentialStore("IntelliJ Platform"))
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