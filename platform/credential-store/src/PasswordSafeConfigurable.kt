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

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.ide.passwordSafe.impl.createPersistentCredentialStore
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.RadioButton
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize
import gnu.trove.THashMap
import java.awt.Component
import javax.swing.JPanel

class PasswordSafeConfigurable(private val settings: PasswordSafeSettings) : ConfigurableBase<PasswordSafeConfigurableUi, PasswordSafeSettings>("application.passwordSafe", "Passwords", "reference.ide.settings.password.safe") {
  override fun getSettings() = settings

  override fun createUi() = PasswordSafeConfigurableUi()
}

class PasswordSafeConfigurableUi : ConfigurableUi<PasswordSafeSettings> {
  private val inKeychain = RadioButton("In Native Keychain")

  private val inKeePass = RadioButton("In KeePass")
  private val keePassMasterPassword = JBPasswordField()

  private val rememberPasswordsUntilClosing = RadioButton("Do not save, forget passwords after restart")

  private val modeToRow = THashMap<ProviderType, Row>()

  override fun reset(settings: PasswordSafeSettings) {
    when (settings.providerType) {
      ProviderType.MEMORY_ONLY -> rememberPasswordsUntilClosing.isSelected = true
      ProviderType.KEYCHAIN -> inKeychain.isSelected = true
      ProviderType.KEEPASS -> inKeePass.isSelected = true
      else -> throw IllegalStateException("Unknown provider type: ${settings.providerType}")
    }

    updateEnabledState()
  }

  override fun isModified(settings: PasswordSafeSettings): Boolean {
    if (getProviderType() != settings.providerType) {
      return true
    }

    if (getProviderType() == ProviderType.KEEPASS && String(keePassMasterPassword.password).nullize(true) != null) {
      return true
    }
    return false
  }

  override fun apply(settings: PasswordSafeSettings) {
    val providerType = getProviderType()
    val passwordSafe = PasswordSafe.getInstance() as PasswordSafeImpl
    var provider = passwordSafe.currentProvider

    val masterPassword = String(keePassMasterPassword.password).nullize(true)?.toByteArray()

    if (settings.providerType != providerType) {
      @Suppress("NON_EXHAUSTIVE_WHEN")
      when (providerType) {
        ProviderType.MEMORY_ONLY -> {
          if (provider is KeePassCredentialStore) {
            provider.memoryOnly = true
            provider.deleteFileStorage()
          }
          else {
            provider = KeePassCredentialStore(memoryOnly = true)
          }
        }

        ProviderType.KEYCHAIN -> {
          provider = createPersistentCredentialStore(provider as? KeePassCredentialStore)
        }

        ProviderType.KEEPASS -> {
          provider = KeePassCredentialStore(memoryOnly = true, existingMasterPassword = masterPassword)
        }
      }
    }

    if (providerType == ProviderType.KEEPASS) {
      if (provider === passwordSafe.currentProvider && masterPassword != null) {
        // so, provider is the same and we must change master password for existing database file
        (provider as KeePassCredentialStore).setMasterPassword(masterPassword)
      }
    }

    settings.providerType = providerType
  }

  fun updateEnabledState() {
    modeToRow[ProviderType.KEEPASS]?.enabled = getProviderType() == ProviderType.KEEPASS
  }

  override fun getComponent(): JPanel {
    val passwordSafe = PasswordSafe.getInstance() as PasswordSafeImpl
    val currentProvider = passwordSafe.currentProvider

    keePassMasterPassword.setPasswordIsStored(true)

    val panel = panel {
      row { label("Save passwords:") }

      buttonGroup({ updateEnabledState() }) {
        if (SystemInfo.isLinux || isMacOsCredentialStoreSupported) {
          row {
            inKeychain()
          }
        }

        row {
          inKeePass()

          modeToRow[ProviderType.KEEPASS] = row("Master Password:") {
            keePassMasterPassword(growPolicy = GrowPolicy.SHORT_TEXT)
          }
        }

        row {
          rememberPasswordsUntilClosing()
        }
        if (currentProvider is KeePassCredentialStore && !currentProvider.memoryOnly) {
          row { hint("Existing KeePass file will be removed.") }
        }
      }

      if (!passwordSafe.isNativeCredentialStoreUsed) {
        row(separated = true) {
          button("Clear Passwords") { event ->
            passwordSafe.clearPasswords()
            Messages.showInfoMessage(event.source as Component, "Passwords were cleared", "Clear Passwords")
          }
        }
      }
    }

    return panel
  }

  private fun getProviderType(): ProviderType {
    return when {
      rememberPasswordsUntilClosing.isSelected -> ProviderType.MEMORY_ONLY
      inKeePass.isSelected -> ProviderType.KEEPASS
      else -> ProviderType.KEYCHAIN
    }
  }
}

internal enum class ProviderType {
  MEMORY_ONLY, KEYCHAIN, KEEPASS,

  // unused, but we cannot remove it because enum value maybe stored in the config and we must correctly deserialize it
  @Deprecated("")
  DO_NOT_STORE
}