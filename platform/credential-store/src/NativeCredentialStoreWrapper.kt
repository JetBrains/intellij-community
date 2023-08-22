// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.credentialStore.keePass.InMemoryCredentialStore
import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.QueueProcessor
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

private val REMOVED_CREDENTIALS = Credentials("REMOVED_CREDENTIALS")

// used only for native keychains, not for KeePass, so `postponedCredentials` and others do not add any overhead when KeePass is used
private class NativeCredentialStoreWrapper(
  private val store: CredentialStore,
  private val queueProcessor: QueueProcessor<() -> Unit>
) : CredentialStore, Closeable {

  constructor(store: CredentialStore): this(store, QueueProcessor<() -> Unit> { it() })

  private val fallbackStore = lazy { InMemoryCredentialStore() }

  private val postponedCredentials = InMemoryCredentialStore()

  private val deniedItems = Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build<CredentialAttributes, Boolean>()

  override fun get(attributes: CredentialAttributes): Credentials? {
    postponedCredentials.get(attributes)?.let {
      return if (it == REMOVED_CREDENTIALS) null else it
    }

    if (attributes.cacheDeniedItems && deniedItems.getIfPresent(attributes) != null) {
      LOG.warn("User denied access to $attributes")
      return ACCESS_TO_KEY_CHAIN_DENIED
    }

    var store = if (fallbackStore.isInitialized()) fallbackStore.value else store
    try {
      val value = store.get(attributes)
      if (attributes.cacheDeniedItems && value === ACCESS_TO_KEY_CHAIN_DENIED) {
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
    if (fallbackStore.isInitialized()) {
      fallbackStore.value.set(attributes, credentials)
      return
    }

    val postponed = credentials ?: REMOVED_CREDENTIALS
    postponedCredentials.set(attributes, postponed)

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
      catch (e: ProcessCanceledException) {
        throw e
      }
      finally {
        val currentPostponed = postponedCredentials.get(attributes)
        if (postponed == currentPostponed) {
          postponedCredentials.set(attributes, null)
        }
      }
    }
  }

  override fun close() {
    if (store is Closeable) {
      queueProcessor.waitFor()
      store.close()
    }
  }
}

private fun notifyUnsatisfiedLinkError(e: UnsatisfiedLinkError) {
  LOG.error(e)
  var message = CredentialStoreBundle.message("notification.content.native.keychain.unavailable",
                                              ApplicationNamesInfo.getInstance().fullProductName)
  if (SystemInfo.isLinux) {
    message += "\n"
    message += CredentialStoreBundle.message("notification.content.native.keychain.unavailable.linux.addition")
  }
  CredentialStoreUiService.getInstance().notify(CredentialStoreBundle.message("notification.title.native.keychain.unavailable"), message, null, null)
}

private class MacOsCredentialStoreFactory : CredentialStoreFactory {
  override fun create(): CredentialStore? = when {
    isMacOsCredentialStoreSupported && JnaLoader.isLoaded() -> NativeCredentialStoreWrapper(KeyChainCredentialStore())
    else -> null
  }
}

private class LinuxCredentialStoreFactory : CredentialStoreFactory {
  override fun create(): CredentialStore? = when {
    SystemInfo.isLinux -> {
      @Suppress("SpellCheckingInspection") val preferWallet = Registry.`is`("credentialStore.linux.prefer.kwallet", false)
      var res: CredentialStore? = if (preferWallet)
        KWalletCredentialStore.create()
      else
        null
      if (res == null && JnaLoader.isLoaded()) {
        try {
          res = SecretCredentialStore.create("com.intellij.credentialStore.Credential")
        }
        catch (e: UnsatisfiedLinkError) {
          res = if (!preferWallet) KWalletCredentialStore.create() else null
          if (res == null) notifyUnsatisfiedLinkError(e)
        }
      }
      if (res == null && !preferWallet) res = KWalletCredentialStore.create()
      res?.let { NativeCredentialStoreWrapper(it) }
    }
    else -> null
  }
}

@TestOnly
fun wrappedInMemory(): CredentialStore = NativeCredentialStoreWrapper(InMemoryCredentialStore(), QueueProcessor<() -> Unit>(
  BiConsumer { item, continuation ->
    try {
      QueueProcessor.runSafely(item)
    }
    finally {
      continuation.run()
    }
  }, true, QueueProcessor.ThreadToUse.AWT, Conditions.alwaysFalse<Any>()))
