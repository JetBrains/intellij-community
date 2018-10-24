// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.keePass.KeePassFileManager
import com.intellij.credentialStore.keePass.MasterKeyFileStorage
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.ide.passwordSafe.impl.createPersistentCredentialStore
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.RadioButton
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.text.nullize
import gnu.trove.THashMap
import java.io.File
import java.nio.file.Paths
import javax.swing.JPanel
import kotlin.properties.Delegates.notNull

internal class PasswordSafeConfigurable(private val settings: PasswordSafeSettings) : ConfigurableBase<PasswordSafeConfigurableUi, PasswordSafeSettings>("application.passwordSafe",
                                                                                                                                                         "Passwords",
                                                                                                                                                         "reference.ide.settings.password.safe") {
  override fun getSettings() = settings

  override fun createUi() = PasswordSafeConfigurableUi()
}

internal fun getDefaultKeePassDbFile() = getDefaultKeePassBaseDirectory().resolve(DB_FILE_NAME)

internal class PasswordSafeConfigurableUi : ConfigurableUi<PasswordSafeSettings> {
  private val inKeychain = RadioButton("In native Keychain")

  private val inKeePass = RadioButton("In KeePass")
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

    @Suppress("IfThenToElvis")
    keePassDbFile.text = settings.keepassDb ?: getDefaultKeePassDbFile().toString()
    updateEnabledState()
  }

  override fun isModified(settings: PasswordSafeSettings): Boolean {
    return getNewProviderType() != settings.providerType || isKeepassFileLocationChanged(settings)
  }

  private fun isKeepassFileLocationChanged(settings: PasswordSafeSettings): Boolean {
    return getNewProviderType() == ProviderType.KEEPASS && getNewDbFileAsString() != settings.keepassDb
  }

  override fun apply(settings: PasswordSafeSettings) {
    val providerType = getNewProviderType()
    val passwordSafe = PasswordSafe.instance as PasswordSafeImpl
    if (settings.providerType != providerType) {
      @Suppress("NON_EXHAUSTIVE_WHEN")
      when (providerType) {
        ProviderType.MEMORY_ONLY -> {
          if (!changeExistingKeepassStoreIfPossible(settings, passwordSafe, isMemoryOnly = true)) {
            passwordSafe.currentProvider = createInMemoryKeePassCredentialStore()
          }
        }

        ProviderType.KEYCHAIN -> {
          passwordSafe.currentProvider = createPersistentCredentialStore()!!
        }

        ProviderType.KEEPASS -> {
          runAndHandleIncorrectMasterPasswordException {
            if (!changeExistingKeepassStoreIfPossible(settings, passwordSafe, isMemoryOnly = false)) {
              passwordSafe.currentProvider = createKeePassCredentialStoreUsingNewOptions()
            }
          }
        }

        else -> throw IllegalStateException("Unknown provider type: $providerType")
      }
    }
    else if (isKeepassFileLocationChanged(settings)) {
      val newDbFile = getNewDbFile()
      if (newDbFile != null) {
        val currentProviderIfComputed = passwordSafe.currentProviderIfComputed as? KeePassCredentialStore
        if (currentProviderIfComputed == null) {
          runAndHandleIncorrectMasterPasswordException {
            passwordSafe.currentProvider = createKeePassCredentialStoreUsingNewOptions()
          }
        }
        else {
          currentProviderIfComputed.dbFile = newDbFile
        }
        settings.keepassDb = newDbFile.toString()
      }
    }

    settings.providerType = providerType
  }

  private fun createKeePassCredentialStoreUsingNewOptions(): KeePassCredentialStore {
    return KeePassCredentialStore(dbFile = getNewDbFileOrError(), masterKeyFile = getDefaultMasterPasswordFile())
  }

  private fun getNewDbFileOrError() = getNewDbFile() ?: throw ConfigurationException("KeePass database path is empty")

  private fun changeExistingKeepassStoreIfPossible(settings: PasswordSafeSettings, passwordSafe: PasswordSafeImpl, isMemoryOnly: Boolean): Boolean {
    if (settings.providerType != ProviderType.MEMORY_ONLY || settings.providerType != ProviderType.KEEPASS) {
      return false
    }

    // must be used only currentProviderIfComputed - no need to compute because it is unsafe operation (incorrect operation and so)
    // if provider not yet computed, we will create a new one in a safe manner (PasswordSafe manager cannot handle correctly - no access to pending master password, cannot throw exceptions)
    val provider = passwordSafe.currentProviderIfComputed as? KeePassCredentialStore ?: return false
    provider.isMemoryOnly = isMemoryOnly
    if (isMemoryOnly) {
      provider.deleteFileStorage()
    }
    else {
      getNewDbFile()?.let {
        provider.dbFile = it
      }
    }
    return true
  }

  private fun getNewDbFile() = getNewDbFileAsString()?.let { Paths.get(it) }

  private fun getNewDbFileAsString() = keePassDbFile.text.trim().nullize()

  private fun updateEnabledState() {
    modeToRow[ProviderType.KEEPASS]?.subRowsEnabled = getNewProviderType() == ProviderType.KEEPASS
  }

  override fun getComponent(): JPanel {
    val passwordSafe = PasswordSafe.instance as PasswordSafeImpl
    return panel {
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
            val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().withFileFilter {
              it.name.endsWith(".kdbx")
            }
            keePassDbFile = textFieldWithBrowseButton("KeePass Database File",
                                                      fileChooserDescriptor = fileChooserDescriptor,
                                                      fileChosen = ::normalizeSelectedFile,
                                                      comment = if (SystemInfo.isWindows) null else "Stored using weak encryption. It is recommended to store on encrypted volume for additional security.")
            gearButton(
              object : DumbAwareAction("Clear") {
                override fun actionPerformed(event: AnActionEvent) {
                  if (!MessageDialogBuilder.yesNo("Clear Passwords", "Are you sure want to remove all passwords?").yesText("Remove Passwords").isYes) {
                    return
                  }

                  LOG.info("Passwords cleared", Error())
                  createKeePassFileManager()?.clear()
                }

                override fun update(e: AnActionEvent) {
                  e.presentation.isEnabled = getNewDbFile()?.exists() ?: false
                }
              },
              object : DumbAwareAction("Import") {
                override fun actionPerformed(event: AnActionEvent) {
                  chooseFile(fileChooserDescriptor, event) {
                    createKeePassFileManager()?.import(Paths.get(normalizeSelectedFile(it)), event)
                    // force reload KeePass Store
                    passwordSafe.closeCurrentProvider()
                  }
                }
              },
              object : DumbAwareAction("${if (MasterKeyFileStorage(getDefaultMasterPasswordFile()).isAutoGenerated()) "Set" else "Change"} Master Password") {
                override fun actionPerformed(event: AnActionEvent) {
                  // even if current provider is not KEEPASS, all actions for db file must be applied immediately (show error if new master password not applicable for existing db file)
                  if (createKeePassFileManager()?.askAndSetMasterKey(event) == true) {
                    if (passwordSafe.settings.providerType == ProviderType.KEEPASS) {
                      // force reload KeePass Store
                      passwordSafe.closeCurrentProvider()
                    }

                    templatePresentation.text = "Change Master Password"
                  }
                }

                override fun update(e: AnActionEvent) {
                  e.presentation.isEnabled = getNewDbFileAsString() != null
                }
              }
            )
          }
        }

        row {
          val comment = when {
            passwordSafe.settings.providerType == ProviderType.KEEPASS -> "Existing KeePass file will be removed."
            else -> null
          }
          rememberPasswordsUntilClosing(comment = comment)
        }
      }
    }
  }

  private fun createKeePassFileManager(): KeePassFileManager? {
    return KeePassFileManager(getNewDbFile() ?: return null, getDefaultMasterPasswordFile())
  }

  private fun getNewProviderType(): ProviderType {
    return when {
      rememberPasswordsUntilClosing.isSelected -> ProviderType.MEMORY_ONLY
      inKeePass.isSelected -> ProviderType.KEEPASS
      else -> ProviderType.KEYCHAIN
    }
  }
}

private fun normalizeSelectedFile(file: VirtualFile): String {
  return when {
    file.isDirectory -> file.path + File.separator + DB_FILE_NAME
    else -> file.path
  }
}

enum class ProviderType {
  MEMORY_ONLY, KEYCHAIN, KEEPASS,

  // unused, but we cannot remove it because enum value maybe stored in the config and we must correctly deserialize it
  @Deprecated("")
  DO_NOT_STORE
}

private inline fun runAndHandleIncorrectMasterPasswordException(handler: () -> Unit) {
  try {
    handler()
  }
  catch (e: IncorrectMasterPasswordException) {
    throw ConfigurationException("Master password for KeePass database is not correct (\"Clear\" can be used to reset database).")
  }
}