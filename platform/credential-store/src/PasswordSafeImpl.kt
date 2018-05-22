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
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.diagnostic.runAndLogException
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.runAsync
import java.nio.file.Paths

fun computeProvider(settings: PasswordSafeSettings): CredentialStore {
  if (settings.providerType == ProviderType.MEMORY_ONLY || (ApplicationManager.getApplication()?.isUnitTestMode ?: false)) {
    return KeePassCredentialStore(memoryOnly = true)
  }
  else if (settings.providerType == ProviderType.KEEPASS) {
    val dbFile = settings.state.keepassDb?.let {
      LOG.runAndLogException { return@let Paths.get(it) }
      return@let null
    }
    return KeePassCredentialStore(dbFile = dbFile)
  }
  else {
    return createPersistentCredentialStore()
  }
}

class PasswordSafeImpl @JvmOverloads constructor(val settings: PasswordSafeSettings /* public - backward compatibility */,
                                                 internal @Volatile var currentProvider: CredentialStore = computeProvider(settings)) : PasswordSafe(), SettingsSavingComponent {
  // it is helper storage to support set password as memory-only (see setPassword memoryOnly flag)
  private val memoryHelperProvider = lazy { KeePassCredentialStore(emptyMap(), memoryOnly = true) }

  override fun isMemoryOnly() = settings.providerType == ProviderType.MEMORY_ONLY

  override fun get(attributes: CredentialAttributes): Credentials? {
    val value = currentProvider.get(attributes)
    if ((value == null || value.password.isNullOrEmpty()) && memoryHelperProvider.isInitialized()) {
      // if password was set as `memoryOnly`
      memoryHelperProvider.value.get(attributes)?.let {
        if (!it.isEmpty()) {
          return it
        }
      }
    }
    return value
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    currentProvider.set(attributes, credentials)
    if (attributes.isPasswordMemoryOnly && !credentials?.password.isNullOrEmpty()) {
      // we must store because otherwise on get will be no password
      memoryHelperProvider.value.set(attributes.toPasswordStoreable(), credentials)
    }
    else if (memoryHelperProvider.isInitialized()) {
      memoryHelperProvider.value.set(attributes, null)
    }
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean) {
    if (memoryOnly) {
      memoryHelperProvider.value.set(attributes.toPasswordStoreable(), credentials)
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
    (currentProvider as? KeePassCredentialStore)?.save()
  }

  fun clearPasswords() {
    LOG.info("Passwords cleared", Error())
    try {
      if (memoryHelperProvider.isInitialized()) {
        memoryHelperProvider.value.clear()
      }
    }
    finally {
      (currentProvider as? KeePassCredentialStore)?.clear()
    }

    ApplicationManager.getApplication().messageBus.syncPublisher(PasswordSafeSettings.TOPIC).credentialStoreCleared()
  }

  override fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean {
    if (isMemoryOnly || credentials.password.isNullOrEmpty()) {
      return true
    }

    if (!memoryHelperProvider.isInitialized()) {
      return false
    }

    return memoryHelperProvider.value.get(attributes)?.let {
      !it.password.isNullOrEmpty()
    } ?: false
  }

  // public - backward compatibility
  @Suppress("unused", "DeprecatedCallableAddReplaceWith")
  @Deprecated("Do not use it")
  val masterKeyProvider: CredentialStore
    get() = currentProvider

  @Suppress("unused")
  @Deprecated("Do not use it")
  // public - backward compatibility
  val memoryProvider: PasswordStorage
    get() = memoryHelperProvider.value
}

internal fun createPersistentCredentialStore(existing: KeePassCredentialStore? = null, convertFileStore: Boolean = false): PasswordStorage {
  LOG.runAndLogException {
    for (factory in CredentialStoreFactory.CREDENTIAL_STORE_FACTORY.extensions) {
      val store = factory.create() ?: continue
      if (convertFileStore) {
        LOG.runAndLogException {
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

@TestOnly
fun createKeePassStore(file: String): PasswordSafe =
  PasswordSafeImpl(
    PasswordSafeSettings().apply { loadState(PasswordSafeSettings.State().apply { providerType = ProviderType.KEEPASS; keepassDb = file }) },
    KeePassCredentialStore(dbFile = Paths.get(file)))