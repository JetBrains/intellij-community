// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.credentialStore.keePass.getDefaultDbFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.messages.Topic
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus

private val defaultProviderType: ProviderType
  get() = CredentialStoreManager.getInstance().defaultProvider()

@ApiStatus.Internal
@State(name = "PasswordSafe",
       category = SettingsCategory.SYSTEM,
       exportable = true,
       storages = [Storage(value = "security.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
class PasswordSafeSettings : PersistentStateComponentWithModificationTracker<PasswordSafeOptions> {
  companion object {
    /**
     * API note: moved to [PasswordSafeSettingsListener.TOPIC]
     */
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<PasswordSafeSettingsListener> = PasswordSafeSettingsListener.TOPIC
  }

  private var state = PasswordSafeOptions()

  var keepassDb: String?
    get() {
      val result = state.keepassDb
      return when {
        result == null && providerType === ProviderType.KEEPASS -> getDefaultDbFile().toString()
        else -> result
      }
    }
    set(value) {
      var v = value.nullize(nullizeSpaces = true)
      if (v != null && v == getDefaultDbFile().toString()) {
        v = null
      }
      state.keepassDb = v
    }

  var providerType: ProviderType
    get() = if (SystemInfo.isWindows && state.provider === ProviderType.KEYCHAIN) ProviderType.KEEPASS else state.provider
    set(value) {
      var newValue = value
      @Suppress("DEPRECATION")
      if (newValue === ProviderType.DO_NOT_STORE) {
        newValue = ProviderType.MEMORY_ONLY
      }

      val oldValue = state.provider
      if (newValue !== oldValue && CredentialStoreManager.getInstance().isSupported(newValue)) {
        state.provider = newValue
        ApplicationManager.getApplication()?.messageBus?.syncPublisher(TOPIC)?.typeChanged(oldValue, newValue)
      }
    }

  override fun getState(): PasswordSafeOptions = state

  override fun loadState(state: PasswordSafeOptions) {
    val credentialStoreManager = CredentialStoreManager.getInstance()
    @Suppress("DEPRECATION")
    if (state.provider === ProviderType.DO_NOT_STORE && !credentialStoreManager.isSupported(ProviderType.MEMORY_ONLY)
        || state.provider !== ProviderType.DO_NOT_STORE && !credentialStoreManager.isSupported(state.provider)) {
      LOG.error("Provider ${state.provider} from loaded credential store config is not supported in this environment")
    }

    with(this.state) {
      isRememberPasswordByDefault = state.isRememberPasswordByDefault
      pgpKeyId = state.pgpKeyId
    }

    providerType = state.provider
    keepassDb = state.keepassDb
  }

  override fun getStateModificationCount(): Long = state.modificationCount
}

@ApiStatus.Internal
class PasswordSafeOptions : BaseState() {
  /**
   * Must be accessed through [PasswordSafeSettings.providerType]
   */
  @get:OptionTag("PROVIDER")
  var provider: ProviderType by enum(defaultProviderType)

  /**
   * Must be accessed through [PasswordSafeSettings.keepassDb]
   */
  var keepassDb: String? by string()

  // Simple properties that don't have special accessors in PasswordSafeSettings
  var isRememberPasswordByDefault: Boolean by property(true)
  var pgpKeyId: String? by string()
}
