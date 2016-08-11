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

import com.intellij.credentialStore.macOs.KeyChainCredentialStore
import com.intellij.credentialStore.macOs.isMacOsCredentialStoreSupported
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.util.SystemProperties

private class CredentialStoreWrapper(private val store: CredentialStore) : PasswordStorage {
  override fun getPassword(requestor: Class<*>?, key: String): String? {
    val rawKey = getRawKey(key, requestor)
    // try old key - as hash
    @Suppress("CanBeVal")
    var value: String?
    try {
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
      store.set(getRawKey(key, requestor), value?.toByteArray())
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