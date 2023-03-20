// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")
package com.intellij.ide.passwordSafe.impl

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.keePass.*
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths

abstract class BasePasswordSafe(private val cs: CoroutineScope) : PasswordSafe() {

  protected abstract val settings: PasswordSafeSettings

  override var isRememberPasswordByDefault: Boolean
    get() = settings.state.isRememberPasswordByDefault
    set(value) {
      settings.state.isRememberPasswordByDefault = value
    }

  private val _currentProvider = SynchronizedClearableLazy { computeProvider(settings) }

  protected val currentProviderIfComputed: CredentialStore?
    get() = if (_currentProvider.isInitialized()) _currentProvider.value else null

  var currentProvider: CredentialStore
    get() = _currentProvider.value
    set(value) {
      _currentProvider.value = value
    }

  fun closeCurrentStore(isSave: Boolean, isEvenMemoryOnly: Boolean) {
    val store = currentProviderIfComputed ?: return
    if (!isEvenMemoryOnly && store is InMemoryCredentialStore) {
      return
    }

    _currentProvider.drop()
    if (isSave && store is KeePassCredentialStore) {
      try {
        store.save(createMasterKeyEncryptionSpec())
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.warn(e)
      }
    }
    else if (store is Closeable) {
      store.close()
    }
  }

  internal fun createMasterKeyEncryptionSpec(): EncryptionSpec =
    when (val pgpKey = settings.state.pgpKeyId) {
      null -> EncryptionSpec(type = getDefaultEncryptionType(), pgpKeyId = null)
      else -> EncryptionSpec(type = EncryptionType.PGP_KEY, pgpKeyId = pgpKey)
    }

  // it is helper storage to support set password as memory-only (see setPassword memoryOnly flag)
  private val memoryHelperProvider: Lazy<CredentialStore> = lazy { InMemoryCredentialStore() }

  override val isMemoryOnly: Boolean
    get() = settings.providerType == ProviderType.MEMORY_ONLY

  override fun get(attributes: CredentialAttributes): Credentials? {
    val value = currentProvider.get(attributes)
    if ((value == null || value.password.isNullOrEmpty()) && memoryHelperProvider.isInitialized()) {
      // if password was set as `memoryOnly`
      memoryHelperProvider.value.get(attributes)?.let {
        return it
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

  // maybe in the future we will use native async, so, this method added here instead "if needed, just use runAsync in your code"
  override fun getAsync(attributes: CredentialAttributes): Promise<Credentials?> {
    return cs.async(Dispatchers.IO) {
      get(attributes)
    }.asCompletableFuture().asPromise()
  }

  open suspend fun save() {
    val keePassCredentialStore = currentProviderIfComputed as? KeePassCredentialStore ?: return
    keePassCredentialStore.save(createMasterKeyEncryptionSpec())
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
}

class TestPasswordSafeImpl @NonInjectable constructor(
  override val settings: PasswordSafeSettings
) : BasePasswordSafe(
  ApplicationManager.getApplication().coroutineScope
) {

  @TestOnly
  constructor() : this(service<PasswordSafeSettings>())

  @TestOnly
  @NonInjectable
  constructor(settings: PasswordSafeSettings, provider: CredentialStore) : this(settings) {
    currentProvider = provider
  }
}

class PasswordSafeImpl(cs: CoroutineScope) : BasePasswordSafe(cs), SettingsSavingComponent {

  override val settings: PasswordSafeSettings
    get() = service<PasswordSafeSettings>()

  // SecureRandom (used to generate master password on first save) can be blocking on Linux
  private val saveAlarm = SingleAlarm.pooledThreadSingleAlarm(delay = 0, ApplicationManager.getApplication()) {
    val currentThread = Thread.currentThread()
    ShutDownTracker.getInstance().executeWithStopperThread(currentThread) {
      (currentProviderIfComputed as? KeePassCredentialStore)?.save(createMasterKeyEncryptionSpec())
    }
  }

  override suspend fun save() {
    val keePassCredentialStore = currentProviderIfComputed as? KeePassCredentialStore ?: return
    if (keePassCredentialStore.isNeedToSave()) {
      saveAlarm.request()
    }
  }
}

fun getDefaultKeePassDbFile(): Path = getDefaultKeePassBaseDirectory().resolve(DB_FILE_NAME)

private fun computeProvider(settings: PasswordSafeSettings): CredentialStore {
  if (settings.providerType == ProviderType.MEMORY_ONLY || (ApplicationManager.getApplication()?.isUnitTestMode == true)) {
    return InMemoryCredentialStore()
  }

  fun showError(@NlsContexts.NotificationTitle title: String) {
    @Suppress("HardCodedStringLiteral")
    CredentialStoreUiService.getInstance().notify(
      title = title,
      content = CredentialStoreBundle.message("notification.content.in.memory.storage"),
      project = null,
      action = NotificationAction.createExpiring(CredentialStoreBundle.message("notification.content.password.settings.action"))
      { e, _ -> CredentialStoreUiService.getInstance().openSettings(e.project) }
    )
  }

  if (CredentialStoreManager.getInstance().isSupported(settings.providerType)) {
    if (settings.providerType == ProviderType.KEEPASS) {
      try {
        val dbFile = settings.keepassDb?.let { Paths.get(it) } ?: getDefaultKeePassDbFile()
        return KeePassCredentialStore(dbFile, getDefaultMasterPasswordFile())
      }
      catch (e: IncorrectMasterPasswordException) {
        LOG.warn(e)
        showError(if (e.isFileMissed) CredentialStoreBundle.message("notification.title.password.missing")
                  else CredentialStoreBundle.message("notification.title.password.incorrect"))
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
        showError(CredentialStoreBundle.message("notification.title.database.error"))
      }
    }
    else {
      try {
        val store = createPersistentCredentialStore()
        if (store == null) {
          showError(CredentialStoreBundle.message("notification.title.keychain.not.available"))
        }
        else {
          return store
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
        showError(CredentialStoreBundle.message("notification.title.cannot.use.keychain"))
      }
    }
  }
  else {
    LOG.error("Provider ${settings.providerType} is not supported in this environment")
    showError(CredentialStoreBundle.message("notification.title.cannot.use.provider", settings.providerType))
  }

  settings.providerType = ProviderType.MEMORY_ONLY
  return InMemoryCredentialStore()
}

fun createPersistentCredentialStore(): CredentialStore? {
  for (factory in CredentialStoreFactory.CREDENTIAL_STORE_FACTORY.extensionList) {
    return factory.create() ?: continue
  }
  return null
}

@TestOnly
fun createKeePassStore(dbFile: Path, masterPasswordFile: Path): PasswordSafe {
  val store = KeePassCredentialStore(dbFile, masterPasswordFile)
  val settings = PasswordSafeSettings()
  settings.loadState(PasswordSafeSettings.PasswordSafeOptions().apply {
    provider = ProviderType.KEEPASS
    keepassDb = store.dbFile.toString()
  })
  return TestPasswordSafeImpl(settings, store)
}

private fun CredentialAttributes.toPasswordStoreable() = if (isPasswordMemoryOnly) CredentialAttributes(serviceName, userName, requestor) else this
