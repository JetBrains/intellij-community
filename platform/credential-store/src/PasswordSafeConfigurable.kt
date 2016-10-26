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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.RadioButton
import com.intellij.ui.components.chars
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize
import gnu.trove.THashMap
import java.io.File
import java.nio.file.Paths
import javax.swing.JPanel
import kotlin.properties.Delegates.notNull

internal class PasswordSafeConfigurable(private val settings: PasswordSafeSettings) : ConfigurableBase<PasswordSafeConfigurableUi, PasswordSafeSettings>("application.passwordSafe", "Passwords", "reference.ide.settings.password.safe") {
  override fun getSettings() = settings

  override fun createUi() = PasswordSafeConfigurableUi()
}

internal fun getDefaultKeePassDbFilePath() = "${PathManager.getConfigPath()}${File.separatorChar}${DB_FILE_NAME}"

internal class PasswordSafeConfigurableUi : ConfigurableUi<PasswordSafeSettings> {
  private val inKeychain = RadioButton("In Native Keychain")

  private val inKeePass = RadioButton("In KeePass")
  private val keePassMasterPassword = JBPasswordField()
  private var keePassDbFile: TextFieldWithHistoryWithBrowseButton by notNull()

  private val rememberPasswordsUntilClosing = RadioButton("Do not save, forget passwords after restart")

  private val modeToRow = THashMap<ProviderType, Row>()

  override fun reset(settings: PasswordSafeSettings) {
    when (settings.providerType) {
      ProviderType.MEMORY_ONLY -> rememberPasswordsUntilClosing.isSelected = true
      ProviderType.KEYCHAIN -> inKeychain.isSelected = true
      ProviderType.KEEPASS -> inKeePass.isSelected = true
      else -> throw IllegalStateException("Unknown provider type: ${settings.providerType}")
    }

    val currentProvider = (PasswordSafe.getInstance() as PasswordSafeImpl).currentProvider
    keePassDbFile.text = settings.keepassDb ?: if (currentProvider is KeePassCredentialStore) currentProvider.dbFile.toString() else getDefaultKeePassDbFilePath()
    updateEnabledState()
  }

  override fun isModified(settings: PasswordSafeSettings): Boolean {
    if (getProviderType() != settings.providerType) {
      return true
    }

    if (getProviderType() == ProviderType.KEEPASS) {
      if (!keePassMasterPassword.chars.isNullOrBlank()) {
        return true
      }

      getCurrentDbFile()?.let {
        val passwordSafe = PasswordSafe.getInstance() as PasswordSafeImpl
        if ((passwordSafe.currentProvider as KeePassCredentialStore).dbFile != it) {
          return true
        }
      }
    }
    return false
  }

  override fun apply(settings: PasswordSafeSettings) {
    val providerType = getProviderType()
    val passwordSafe = PasswordSafe.getInstance() as PasswordSafeImpl
    var provider = passwordSafe.currentProvider

    val masterPassword = keePassMasterPassword.chars.toString().nullize(true)?.toByteArray()

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
          provider = KeePassCredentialStore(existingMasterPassword = masterPassword, dbFile = getCurrentDbFile())
        }
      }
    }

    val newProvider = provider
    if (newProvider === passwordSafe.currentProvider && newProvider is KeePassCredentialStore) {
      if (masterPassword != null) {
        // so, provider is the same and we must change master password for existing database file
        newProvider.setMasterPassword(masterPassword)
      }

      getCurrentDbFile()?.let {
        newProvider.dbFile = it
      }
    }

    settings.providerType = providerType
    if (newProvider is KeePassCredentialStore) {
      settings.keepassDb = newProvider.dbFile.toString()
    }
    else {
      settings.keepassDb = null
    }
    passwordSafe.currentProvider = newProvider
  }

  fun getCurrentDbFile() = keePassDbFile.text.trim().nullize()?.let { Paths.get(it) }

  fun updateEnabledState() {
    modeToRow[ProviderType.KEEPASS]?.subRowsEnabled = getProviderType() == ProviderType.KEEPASS
  }

  override fun getComponent(): JPanel {
    val passwordSafe = PasswordSafe.getInstance() as PasswordSafeImpl

    keePassMasterPassword.setPasswordIsStored(true)

    val panel = panel {
      row { label("Save passwords:") }

      buttonGroup({ updateEnabledState() }) {
        if (SystemInfo.isLinux || isMacOsCredentialStoreSupported) {
          row {
            inKeychain()
          }
        }

        modeToRow[ProviderType.KEEPASS] = row {
          inKeePass()
          row("Database:") {
            keePassDbFile = textFieldWithBrowseButton("KeePass Database File",
                                                      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().withFileFilter {
                                                        it.name.endsWith(".kdbx")
                                                      },
                                                      fileChoosen = {
                                                        if (it.isDirectory) {
                                                          it.path + File.separator + DB_FILE_NAME
                                                        }
                                                        else {
                                                          it.path
                                                        }
                                                      })
            gearButton(
                object : AnAction("Clear") {
                  override fun actionPerformed(event: AnActionEvent) {
                    if (MessageDialogBuilder.yesNo("Clear Passwords", "Are you sure want to remove all passwords?").yesText("Remove Passwords").isYes) {
                      passwordSafe.clearPasswords()
                    }
                  }
                },
                object : AnAction("Import") {
                  override fun actionPerformed(e: AnActionEvent?) {

                  }
                }
            )
          }
          row("Master Password:") {
            keePassMasterPassword(growPolicy = GrowPolicy.SHORT_TEXT)
          }
        }

        row {
          rememberPasswordsUntilClosing()
        }

        val currentProvider = passwordSafe.currentProvider
        if (currentProvider is KeePassCredentialStore && !currentProvider.memoryOnly) {
          row { hint("Existing KeePass file will be removed.") }
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