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
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.util.containers.ContainerUtil

private val nullPassword = Credentials("\u0000", OneTimeString("\u0000"))

private val NOTIFICATION_MANAGER by lazy {
  // we use name "Password Safe" instead of "Credentials Store" because it was named so previously (and no much sense to rename it)
  SingletonNotificationManager(NotificationGroup.balloonGroup("Password Safe"), NotificationType.WARNING, null)
}

private class CredentialStoreWrapper(private val store: CredentialStore) : PasswordStorage {
  private val fallbackStore = lazy { KeePassCredentialStore(memoryOnly = true) }

  private val queueProcessor = QueueProcessor<() -> Unit>({
                                                            it()
                                                          })

  private val postponedCredentials = ContainerUtil.newConcurrentMap<CredentialAttributes, Credentials>()

  override fun get(attributes: CredentialAttributes): Credentials? {
    postponedCredentials.get(attributes)?.let {
      return if (it == nullPassword) null else it
    }

    var store = if (fallbackStore.isInitialized()) fallbackStore.value else store
    val requestor = attributes.requestor
    val userName = attributes.userName
    try {
      val value = store.get(attributes)
      if (value != null || requestor == null || userName == null) {
        return value
      }
    }
    catch (e: UnsatisfiedLinkError) {
      store = fallbackStore.value
      LOG.error(e)
      NOTIFICATION_MANAGER.notify("Native Keychain is not used", "Failed to use native keychain — credentials will be stored in memory (until application is closed).")
      return store.get(attributes)
    }
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }

    LOG.catchAndLog {
      fun setNew(oldKey: CredentialAttributes): Credentials? {
        store.get(oldKey)?.let {
          set(oldKey, null)

          // https://youtrack.jetbrains.com/issue/IDEA-160341
          fun createCredentials() = Credentials(userName, it.password?.toCharArray(false)?.let { OneTimeString(it) })
          set(attributes, createCredentials())
          return createCredentials()
        }
        return null
      }

      // try old key - as hash
      setNew(toOldKey(requestor, userName))?.let { return it }

      val appInfo = ApplicationInfoEx.getInstanceEx()
      if (appInfo.isEAP || appInfo.build.isSnapshot) {
        setNew(CredentialAttributes("IntelliJ Platform", "${requestor.name}/$userName"))?.let { return it }
      }
    }
    return null
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