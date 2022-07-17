// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.impl.getDefaultKeePassDbFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.messages.Topic
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.annotations.OptionTag

@Service
@State(name = "PasswordSafe", storages = [Storage(value = "security.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
class PasswordSafeSettings : PersistentStateComponentWithModificationTracker<PasswordSafeSettings.PasswordSafeOptions> {
  companion object {
    private val LOG = logger<PasswordSafeSettings>()

    @JvmField
    val TOPIC = Topic.create("PasswordSafeSettingsListener", PasswordSafeSettingsListener::class.java)

    private val defaultProviderType: ProviderType
      get() = CredentialStoreManager.getInstance().defaultProvider()
  }

  private var state = PasswordSafeOptions()

  var keepassDb: String?
    get() {
      val result = state.keepassDb
      return when {
        result == null && providerType === ProviderType.KEEPASS -> getDefaultKeePassDbFile().toString()
        else -> result
      }
    }
    set(value) {
      var v = value.nullize(nullizeSpaces = true)
      if (v != null && v == getDefaultKeePassDbFile().toString()) {
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

  override fun getState() = state

  override fun loadState(state: PasswordSafeOptions) {
    val credentialStoreManager = CredentialStoreManager.getInstance()
    @Suppress("DEPRECATION")
    if (state.provider === ProviderType.DO_NOT_STORE && !credentialStoreManager.isSupported(ProviderType.MEMORY_ONLY)
        || state.provider !== ProviderType.DO_NOT_STORE && !credentialStoreManager.isSupported(state.provider)) {
      LOG.error("Provider ${state.provider} from loaded credential store config is not supported in this environment")
    }

    this.state = state
    providerType = state.provider
    state.keepassDb = state.keepassDb.nullize(nullizeSpaces = true)
  }

  override fun getStateModificationCount() = state.modificationCount

  class PasswordSafeOptions : BaseState() {
    // do not use it directly
    @get:OptionTag("PROVIDER")
    var provider by enum(defaultProviderType)

    // do not use it directly
    var keepassDb by string()
    var isRememberPasswordByDefault by property(true)

    // do not use it directly
    var pgpKeyId by string()
  }
}
