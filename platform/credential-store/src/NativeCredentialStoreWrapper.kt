// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.google.common.cache.CacheBuilder
import com.intellij.credentialStore.keePass.InMemoryCredentialStore
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.TimeUnit

internal val NOTIFICATION_MANAGER by lazy {
  // we use name "Password Safe" instead of "Credentials Store" because it was named so previously (and no much sense to rename it)
  SingletonNotificationManager(NotificationGroup("Password Safe", NotificationDisplayType.STICKY_BALLOON, true), NotificationType.ERROR)
}

// used only for native keychains, not for KeePass, so, postponedCredentials and other is not overhead if KeePass is used
private class NativeCredentialStoreWrapper(private val store: CredentialStore) : CredentialStore {
  private val fallbackStore = lazy { InMemoryCredentialStore() }

  private val queueProcessor = QueueProcessor<() -> Unit> { it() }

  private val postponedCredentials = InMemoryCredentialStore()
  private val postponedRemovedCredentials = ContainerUtil.newConcurrentSet<CredentialAttributes>()

  private val deniedItems = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build<CredentialAttributes, Boolean>()

  override fun get(attributes: CredentialAttributes): Credentials? {
    if (postponedRemovedCredentials.contains(attributes)) {
      return null
    }
    postponedCredentials.get(attributes)?.let {
      return it
    }

    if (deniedItems.getIfPresent(attributes) != null) {
      LOG.warn("User denied access to $attributes")
      return null
    }

    var store = if (fallbackStore.isInitialized()) fallbackStore.value else store
    try {
      val value = store.get(attributes)
      if (value === ACCESS_TO_KEY_CHAIN_DENIED) {
        deniedItems.put(attributes, true)
      }
      return value
    }
    catch (e: UnsatisfiedLinkError) {
      store = fallbackStore.value
      notifyUnsatisfiedLinkError(e)
      return store.get(attributes)
    }
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    LOG.runAndLogException {
      if (fallbackStore.isInitialized()) {
        fallbackStore.value.set(attributes, credentials)
        return
      }

      if (credentials == null) {
        postponedRemovedCredentials.add(attributes)
      }
      else {
        postponedCredentials.set(attributes, credentials)
      }

      queueProcessor.add {
        try {
          var store = if (fallbackStore.isInitialized()) fallbackStore.value else store
          try {
            store.set(attributes, credentials)
          }
          catch (e: UnsatisfiedLinkError) {
            store = fallbackStore.value
            notifyUnsatisfiedLinkError(e)
            store.set(attributes, credentials)
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
        finally {
          if (!postponedRemovedCredentials.remove(attributes)) {
            postponedCredentials.set(attributes, null)
          }
        }
      }
    }
  }
}

private fun notifyUnsatisfiedLinkError(e: UnsatisfiedLinkError) {
  LOG.error(e)
  var message = "Credentials are remembered until ${ApplicationNamesInfo.getInstance().fullProductName} is closed."
  if (SystemInfo.isLinux) {
    message += "\nPlease install required package libsecret-1-0: sudo apt-get install libsecret-1-0 gnome-keyring"
  }
  NOTIFICATION_MANAGER.notify("Cannot Access Native Keychain", message)
}

private class MacOsCredentialStoreFactory : CredentialStoreFactory {
  override fun create(): CredentialStore? {
    if (isMacOsCredentialStoreSupported) {
      return NativeCredentialStoreWrapper(KeyChainCredentialStore())
    }
    return null
  }
}

private class LinuxSecretCredentialStoreFactory : CredentialStoreFactory {
  override fun create(): CredentialStore? {
    if (SystemInfo.isLinux) {
      return NativeCredentialStoreWrapper(SecretCredentialStore("com.intellij.credentialStore.Credential"))
    }
    return null
  }
}