// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("PackageDirectoryMismatch")

package com.intellij.ide.passwordSafe.impl

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.keePass.*
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.pooledThreadSingleAlarm
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.runAsync
import java.nio.file.Path
import java.nio.file.Paths

open class BasePasswordSafe @JvmOverloads constructor(val settings: PasswordSafeSettings /* public - backward compatibility */,
                                                      provider: CredentialStore? = null /* TestOnly */) : PasswordSafe() {
  override var isRememberPasswordByDefault: Boolean
    get() = settings.state.isRememberPasswordByDefault
    set(value) {
      settings.state.isRememberPasswordByDefault = value
    }

  private val _currentProvider = SynchronizedClearableLazy { computeProvider(settings) }

  protected val currentProviderIfComputed: CredentialStore?
    get() = if (_currentProvider.isInitialized()) _currentProvider.value else null

  internal var currentProvider: CredentialStore
    get() = _currentProvider.value
    set(value) {
      _currentProvider.value = value
    }

  internal fun closeCurrentStore(isSave: Boolean, isEvenMemoryOnly: Boolean) {
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
  }

  internal fun createMasterKeyEncryptionSpec(): EncryptionSpec {
    val pgpKey = settings.state.pgpKeyId
    return when (pgpKey) {
      null -> EncryptionSpec(type = getDefaultEncryptionType(), pgpKeyId = null)
      else -> EncryptionSpec(type = EncryptionType.PGP_KEY, pgpKeyId = pgpKey)
    }
  }

  // it is helper storage to support set password as memory-only (see setPassword memoryOnly flag)
  protected val memoryHelperProvider: Lazy<CredentialStore> = lazy { InMemoryCredentialStore() }

  override val isMemoryOnly: Boolean
    get() = settings.providerType == ProviderType.MEMORY_ONLY

  init {
    provider?.let {
      currentProvider = it
    }
  }

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
  override fun getAsync(attributes: CredentialAttributes): Promise<Credentials?> = runAsync { get(attributes) }

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

class PasswordSafeImpl(settings: PasswordSafeSettings /* public - backward compatibility */) : BasePasswordSafe(settings), SettingsSavingComponent {
  // SecureRandom (used to generate master password on first save) can be blocking on Linux
  private val saveAlarm = pooledThreadSingleAlarm(delay = 0) {
    val currentThread = Thread.currentThread()
    ShutDownTracker.getInstance().registerStopperThread(currentThread)
    try {
      (currentProviderIfComputed as? KeePassCredentialStore)?.save(createMasterKeyEncryptionSpec())
    }
    finally {
      ShutDownTracker.getInstance().unregisterStopperThread(currentThread)
    }
  }

  override suspend fun save() {
    val keePassCredentialStore = currentProviderIfComputed as? KeePassCredentialStore ?: return
    if (keePassCredentialStore.isNeedToSave()) {
      saveAlarm.request()
    }
  }

  @Suppress("unused", "DeprecatedCallableAddReplaceWith")
  @Deprecated("Do not use it")
  // public - backward compatibility
  val memoryProvider: PasswordStorage
    get() = memoryHelperProvider.value as PasswordStorage
}

internal fun getDefaultKeePassDbFile() = getDefaultKeePassBaseDirectory().resolve(DB_FILE_NAME)

private fun computeProvider(settings: PasswordSafeSettings): CredentialStore {
  if (settings.providerType == ProviderType.MEMORY_ONLY || (ApplicationManager.getApplication()?.isUnitTestMode == true)) {
    return InMemoryCredentialStore()
  }

  fun showError(title: String) {
    NOTIFICATION_MANAGER.notify(title = title, content = "In-memory password storage will be used.", action = object: NotificationAction("Passwords Settings") {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        // to hide before Settings open, otherwise dialog and notification are shown at the same time
        notification.expire()
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, PasswordSafeConfigurable::class.java)
      }
    })
  }

  if (settings.providerType == ProviderType.KEEPASS) {
    try {
      val dbFile = settings.keepassDb?.let { Paths.get(it) } ?: getDefaultKeePassDbFile()
      return KeePassCredentialStore(dbFile, getDefaultMasterPasswordFile())
    }
    catch (e: IncorrectMasterPasswordException) {
      LOG.warn(e)
      showError("KeePass master password is ${if (e.isFileMissed) "missing" else "incorrect"}")
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      showError("Failed opening KeePass database")
    }
  }
  else {
    try {
      val store = createPersistentCredentialStore()
      if (store == null) {
        showError("Native keychain is not available")
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
      showError("Cannot use native keychain")
    }
  }

  settings.providerType = ProviderType.MEMORY_ONLY
  return InMemoryCredentialStore()
}

internal fun createPersistentCredentialStore(): CredentialStore? {
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
  return BasePasswordSafe(settings, store)
}

private fun CredentialAttributes.toPasswordStoreable() = if (isPasswordMemoryOnly) CredentialAttributes(serviceName, userName, requestor) else this