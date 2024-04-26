// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")
package com.intellij.ide.passwordSafe.impl

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.IncorrectMainPasswordException
import com.intellij.credentialStore.keePass.*
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.NlsContexts
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths

private val LOG: Logger
  get() = logger<CredentialStore>()

abstract class BasePasswordSafe(private val coroutineScope: CoroutineScope) : PasswordSafe() {
  protected abstract val settings: PasswordSafeSettings

  override var isRememberPasswordByDefault: Boolean
    get() = settings.state.isRememberPasswordByDefault
    set(value) {
      settings.state.isRememberPasswordByDefault = value
    }

  private val _currentProvider = SynchronizedClearableLazy { computeProvider(settings) }

  private val currentProviderIfComputed: CredentialStore?
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
        store.save(createMainKeyEncryptionSpec())
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

  private fun createMainKeyEncryptionSpec(): EncryptionSpec {
    return when (val pgpKey = settings.state.pgpKeyId) {
      null -> EncryptionSpec(type = getDefaultEncryptionType(), pgpKeyId = null)
      else -> EncryptionSpec(type = EncryptionType.PGP_KEY, pgpKeyId = pgpKey)
    }
  }

  // it is a helper storage to support set password as memory-only (see setPassword memoryOnly flag)
  private val memoryHelperProvider: Lazy<CredentialStore> = lazy { InMemoryCredentialStore() }

  override val isMemoryOnly: Boolean
    get() = settings.providerType == ProviderType.MEMORY_ONLY

  override fun get(attributes: CredentialAttributes): Credentials? {
    SlowOperations.assertNonCancelableSlowOperationsAreAllowed()
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
    SlowOperations.assertNonCancelableSlowOperationsAreAllowed()
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
      // remove to ensure that on getPassword we will not return some value from the default provider
      currentProvider.set(attributes, null)
    }
    else {
      set(attributes, credentials)
    }
  }

  // maybe in the future we will use native async; this method added here instead "if needed, just use runAsync in your code"
  override fun getAsync(attributes: CredentialAttributes): Promise<Credentials?> {
    return coroutineScope.async(Dispatchers.IO) {
      get(attributes)
    }.asCompletableFuture().asPromise()
  }

  suspend fun save() {
    val keePassCredentialStore = currentProviderIfComputed as? KeePassCredentialStore ?: return
    withContext(Dispatchers.IO) {
      keePassCredentialStore.save(createMainKeyEncryptionSpec())
    }
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

@TestOnly
class TestPasswordSafeImpl @NonInjectable constructor(
  override val settings: PasswordSafeSettings
) : BasePasswordSafe(coroutineScope = (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope()) {
  @TestOnly
  constructor() : this(service<PasswordSafeSettings>())

  @TestOnly
  @NonInjectable
  constructor(settings: PasswordSafeSettings, provider: CredentialStore) : this(settings) {
    currentProvider = provider
  }
}

@Internal
class PasswordSafeImpl(coroutineScope: CoroutineScope) : BasePasswordSafe(coroutineScope), SettingsSavingComponent {
  override val settings: PasswordSafeSettings
    get() = service<PasswordSafeSettings>()
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
        return KeePassCredentialStore(dbFile, getDefaultMainPasswordFile())
      }
      catch (e: IncorrectMainPasswordException) {
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
fun createKeePassStore(dbFile: Path, mainPasswordFile: Path): PasswordSafe {
  val store = KeePassCredentialStore(dbFile, mainPasswordFile)
  val settings = PasswordSafeSettings()
  settings.loadState(PasswordSafeOptions().apply {
    provider = ProviderType.KEEPASS
    keepassDb = store.dbFile.toString()
  })
  return TestPasswordSafeImpl(settings, store)
}

private fun CredentialAttributes.toPasswordStoreable(): CredentialAttributes {
  return if (isPasswordMemoryOnly) CredentialAttributes(serviceName, userName, requestor) else this
}
