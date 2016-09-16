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
@file:Suppress("PackageDirectoryMismatch")

package com.intellij.ide.passwordSafe.impl

import com.intellij.credentialStore.*
import com.intellij.credentialStore.PasswordSafeSettings.ProviderType
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.diagnostic.catchAndLog
import org.jetbrains.concurrency.runAsync

class PasswordSafeImpl(/* public - backward compatibility */val settings: PasswordSafeSettings) : PasswordSafe(), SettingsSavingComponent {
  private @Volatile var currentProvider: PasswordStorage

  // it is helper storage to support set password as memory-only (see setPassword memoryOnly flag)
  private val memoryHelperProvider = lazy { KeePassCredentialStore(emptyMap(), memoryOnly = true) }

  override fun isMemoryOnly() = settings.providerType == ProviderType.MEMORY_ONLY

  val isNativeCredentialStoreUsed: Boolean
    get() = currentProvider !is KeePassCredentialStore

  init {
    if (settings.providerType == ProviderType.MEMORY_ONLY || ApplicationManager.getApplication().isUnitTestMode) {
      currentProvider = KeePassCredentialStore(memoryOnly = true)
    }
    else {
      currentProvider = createPersistentCredentialStore()
    }

    ApplicationManager.getApplication().messageBus.connect().subscribe(PasswordSafeSettings.TOPIC, object: PasswordSafeSettingsListener {
      override fun typeChanged(oldValue: ProviderType, newValue: ProviderType) {
        val memoryOnly = newValue == ProviderType.MEMORY_ONLY
        if (memoryOnly) {
          val provider = currentProvider
          if (provider is KeePassCredentialStore) {
            provider.memoryOnly = true
            provider.deleteFileStorage()
          }
          else {
            currentProvider = KeePassCredentialStore(memoryOnly = true)
          }
        }
        else {
          currentProvider = createPersistentCredentialStore(currentProvider as? KeePassCredentialStore)
        }
      }
    })
  }

  override fun get(attributes: CredentialAttributes): Credentials? {
    val value = currentProvider.get(attributes)
    if (value == null && memoryHelperProvider.isInitialized()) {
      // if password was set as `memoryOnly`
      return memoryHelperProvider.value.get(attributes)
    }
    return value
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    currentProvider.set(attributes, credentials)
    if (memoryHelperProvider.isInitialized()) {
      val memoryHelper = memoryHelperProvider.value
      // update password in the memory helper, but only if it was previously set
      if (credentials == null || memoryHelper.get(attributes) != null) {
        memoryHelper.set(attributes, credentials)
      }
    }
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean) {
    if (memoryOnly) {
      memoryHelperProvider.value.set(attributes, credentials)
      // remove to ensure that on getPassword we will not return some value from default provider
      currentProvider.set(attributes, null)
    }
    else {
      set(attributes, credentials)
    }
  }

  // maybe in the future we will use native async, so, this method added here instead "if need, just use runAsync in your code"
  override fun getAsync(attributes: CredentialAttributes) = runAsync { get(attributes) }

  override fun save() {
    (currentProvider as? KeePassCredentialStore)?.let { it.save() }
  }

  fun clearPasswords() {
    LOG.info("Passwords cleared", Error())
    try {
      if (memoryHelperProvider.isInitialized()) {
        memoryHelperProvider.value.clear()
      }
    }
    finally {
      (currentProvider as? KeePassCredentialStore)?.let { it.clear() }
    }

    ApplicationManager.getApplication().messageBus.syncPublisher(PasswordSafeSettings.TOPIC).credentialStoreCleared()
  }

  // public - backward compatibility
  @Suppress("unused", "DeprecatedCallableAddReplaceWith")
  @Deprecated("Do not use it")
  val masterKeyProvider: PasswordStorage
    get() = currentProvider

  @Suppress("unused")
  @Deprecated("Do not use it")
  // public - backward compatibility
  val memoryProvider: PasswordStorage
    get() = memoryHelperProvider.value
}

private fun createPersistentCredentialStore(existing: KeePassCredentialStore? = null, convertFileStore: Boolean = false): PasswordStorage {
  LOG.catchAndLog {
    for (factory in CredentialStoreFactory.CREDENTIAL_STORE_FACTORY.extensions) {
      val store = factory.create() ?: continue
      if (convertFileStore) {
        LOG.catchAndLog {
          val fileStore = KeePassCredentialStore()
          fileStore.copyTo(store)
          fileStore.clear()
          fileStore.save()
        }
      }
      return store
    }
  }

  existing?.let {
    it.memoryOnly = false
    return it
  }
  return KeePassCredentialStore()
}