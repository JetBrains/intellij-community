// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.gpg.Pgp
import com.intellij.credentialStore.gpg.PgpKey
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.keePass.DB_FILE_NAME
import com.intellij.credentialStore.keePass.KeePassFileManager
import com.intellij.credentialStore.keePass.MasterKeyFileStorage
import com.intellij.credentialStore.keePass.getDefaultMasterPasswordFile
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.ide.passwordSafe.impl.createPersistentCredentialStore
import com.intellij.ide.passwordSafe.impl.getDefaultKeePassDbFile
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.ui.components.RadioButton
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import com.intellij.util.text.nullize
import gnu.trove.THashMap
import java.io.File
import java.nio.file.Paths
import javax.swing.JList
import javax.swing.JPanel

internal class PasswordSafeConfigurable(private val settings: PasswordSafeSettings) : ConfigurableBase<PasswordSafeConfigurableUi, PasswordSafeSettings>("application.passwordSafe",
                                                                                                                                                         "Passwords",
                                                                                                                                                         "reference.ide.settings.password.safe") {
  override fun getSettings() = settings

  override fun createUi() = PasswordSafeConfigurableUi()
}

internal class PasswordSafeConfigurableUi : ConfigurableUi<PasswordSafeSettings> {
  private val inKeychain = RadioButton("In native Keychain")

  private val inKeePass = RadioButton("In KeePass")
  private var keePassDbFile: TextFieldWithBrowseButton? = null

  private val pgpKeyModel = CollectionComboBoxModel<PgpKey?>()

  private val rememberPasswordsUntilClosing = RadioButton("Do not save, forget passwords after restart")

  private val modeToRow = THashMap<ProviderType, Row>()

  private val pgp by lazy { Pgp() }

  // https://youtrack.jetbrains.com/issue/IDEA-200188
  // reuse to avoid delays - on Linux SecureRandom is quite slow
  private val secureRandom = lazy { createSecureRandom() }

  override fun reset(settings: PasswordSafeSettings) {
    when (settings.providerType) {
      ProviderType.MEMORY_ONLY -> rememberPasswordsUntilClosing.isSelected = true
      ProviderType.KEYCHAIN -> inKeychain.isSelected = true
      ProviderType.KEEPASS -> inKeePass.isSelected = true
      else -> throw IllegalStateException("Unknown provider type: ${settings.providerType}")
    }

    @Suppress("IfThenToElvis")
    keePassDbFile?.text = settings.keepassDb ?: getDefaultKeePassDbFile().toString()
    updateEnabledState()

    val secretKeys = pgp.listKeys()
    pgpKeyModel.removeAll()
    pgpKeyModel.add(null)
    pgpKeyModel.addAll(1, secretKeys)

    val currentKeyId = settings.state.pgpKeyId
    pgpKeyModel.selectedItem = if (currentKeyId == null) null else secretKeys.firstOrNull { it.keyId == currentKeyId }
  }

  override fun isModified(settings: PasswordSafeSettings): Boolean {
    if (keePassDbFile == null) {
      return false
    }
    return getNewProviderType() != settings.providerType || isKeepassFileLocationChanged(settings) || isPgpKeyChanged(settings)
  }

  private fun isPgpKeyChanged(settings: PasswordSafeSettings) = settings.state.pgpKeyId != pgpKeyModel.selected?.keyId

  private fun isKeepassFileLocationChanged(settings: PasswordSafeSettings): Boolean {
    return getNewProviderType() == ProviderType.KEEPASS && getNewDbFileAsString() != settings.keepassDb
  }

  override fun apply(settings: PasswordSafeSettings) {
    val providerType = getNewProviderType()

    // close if any, it is more reliable just close current store and later it will be recreated lazily with a new settings
    (PasswordSafe.instance as PasswordSafeImpl).closeCurrentStore(isSave = false, isEvenMemoryOnly = providerType != ProviderType.MEMORY_ONLY)

    val passwordSafe = PasswordSafe.instance as PasswordSafeImpl
    @Suppress("CascadeIf")
    if (settings.providerType != providerType) {
      @Suppress("NON_EXHAUSTIVE_WHEN")
      when (providerType) {
        ProviderType.MEMORY_ONLY -> {
          // nothing else is required to setup
        }

        ProviderType.KEYCHAIN -> {
          // create here to ensure that user will get any error during native store creation
          try {
            val store = createPersistentCredentialStore()
            if (store == null) {
              throw ConfigurationException("Internal error, no available credential store implementation.")
            }
            passwordSafe.currentProvider = store
          }
          catch (e: UnsatisfiedLinkError) {
            LOG.warn(e)
            if (SystemInfo.isLinux) {
              throw ConfigurationException("Package libsecret-1-0 is not installed (to install: sudo apt-get install libsecret-1-0 gnome-keyring).")
            }
            else {
              throw ConfigurationException(e.message)
            }
          }
        }

        ProviderType.KEEPASS -> createAndSaveKeePassDatabaseWithNewOptions(settings)
        else -> throw ConfigurationException("Unknown provider type: $providerType")
      }
    }
    else if (isKeepassFileLocationChanged(settings)) {
      createAndSaveKeePassDatabaseWithNewOptions(settings)
    }
    else if (providerType == ProviderType.KEEPASS && isPgpKeyChanged(settings)) {
      // not our business in this case, if there is no db file, do not require not null KeePassFileManager
      createKeePassFileManager()?.saveMasterKeyToApplyNewEncryptionSpec()
    }

    // not in createAndSaveKeePassDatabaseWithNewOptions (as logically should be) because we want to force users to set custom master passwords even if some another setting (not path) was changed
    // (e.g. PGP key)
    if (providerType == ProviderType.KEEPASS) {
      createKeePassFileManager()?.setCustomMasterPasswordIfNeed(getDefaultKeePassDbFile())
    }

    settings.providerType = providerType
    settings.state.pgpKeyId = pgpKeyModel.selected?.keyId
  }

  // existing in-memory KeePass database is not used, the same as if switched to KEYCHAIN
  // for KeePass not clear - should we append in-memory credentials to existing database or not
  // (and if database doesn't exist, should we append or not), so, wait first user request (prefer to keep implementation simple)
  private fun createAndSaveKeePassDatabaseWithNewOptions(settings: PasswordSafeSettings) {
    val newDbFile = getNewDbFile() ?: throw ConfigurationException("KeePass database path is empty.")
    if (newDbFile.isDirectory()) {
      // we do not normalize as we do on file choose because if user decoded to type path manually,
      // it should be valid path and better to avoid any magic here
      throw ConfigurationException("KeePass database file is directory.")
    }
    if (!newDbFile.fileName.toString().endsWith(".kdbx")) {
      throw ConfigurationException("KeePass database file should ends with \".kdbx\".")
    }

    settings.keepassDb = newDbFile.toString()

    try {
      KeePassFileManager(newDbFile, getDefaultMasterPasswordFile(), getEncryptionSpec(), secureRandom).useExisting()
    }
    catch (e: IncorrectMasterPasswordException) {
      throw ConfigurationException("Master password for KeePass database is not correct (\"Clear\" can be used to reset database).")
    }
    catch (e: Exception) {
      LOG.error(e)
      throw ConfigurationException("Internal error: ${e.message}")
    }
  }

  private fun getNewDbFile() = getNewDbFileAsString()?.let { Paths.get(it) }

  private fun getNewDbFileAsString() = keePassDbFile!!.text.trim().nullize()

  private fun updateEnabledState() {
    modeToRow[ProviderType.KEEPASS]?.subRowsEnabled = getNewProviderType() == ProviderType.KEEPASS
  }

  override fun getComponent(): JPanel {
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
              it.isDirectory || it.name.endsWith(".kdbx")
            }
            keePassDbFile = textFieldWithBrowseButton("KeePass Database File",
                                                      fileChooserDescriptor = fileChooserDescriptor,
                                                      fileChosen = {
                                                        when {
                                                          it.isDirectory -> "${it.path}${File.separator}$DB_FILE_NAME"
                                                          else -> it.path
                                                        }
                                                      },
                                                      comment = if (SystemInfo.isWindows) null else "Stored using weak encryption. It is recommended to store on encrypted volume for additional security.")
            gearButton(
              ClearKeePassDatabaseAction(),
              ImportKeePassDatabaseAction(),
              ChangeKeePassDatabaseMasterPasswordAction()
            )
          }
          row("PGP key:") {
            val comboBox = ComboBox(pgpKeyModel)
            comboBox.setRenderer(object : ListCellRendererWrapper<PgpKey?>() {
              override fun customize(list: JList<*>, value: PgpKey?, index: Int, selected: Boolean, hasFocus: Boolean) {
                if (value == null) {
                  setText(if (list.model.size == 1) "No keys" else "Do not use")
                }
                else {
                  setText("${value.userId} (${value.keyId})")
                }
              }
            })
            comboBox(comment = "Protect master key file using PGP.", growPolicy = GrowPolicy.MEDIUM_TEXT)
          }
        }

        row {
          rememberPasswordsUntilClosing()
        }
      }
    }
  }

  private fun createKeePassFileManager(): KeePassFileManager? {
    return KeePassFileManager(getNewDbFile() ?: return null, getDefaultMasterPasswordFile(), getEncryptionSpec(), secureRandom)
  }

  private fun getEncryptionSpec(): EncryptionSpec {
    val pgpKey = pgpKeyModel.selected
    return when (pgpKey) {
      null -> EncryptionSpec(type = getDefaultEncryptionType(), pgpKeyId = null)
      else -> EncryptionSpec(type = EncryptionType.PGP_KEY, pgpKeyId = pgpKey.keyId)
    }
  }

  private fun getNewProviderType(): ProviderType {
    return when {
      rememberPasswordsUntilClosing.isSelected -> ProviderType.MEMORY_ONLY
      inKeePass.isSelected -> ProviderType.KEEPASS
      else -> ProviderType.KEYCHAIN
    }
  }

  private inner class ClearKeePassDatabaseAction : DumbAwareAction("Clear") {
    override fun actionPerformed(event: AnActionEvent) {
      if (!MessageDialogBuilder.yesNo("Clear Passwords", "Are you sure want to remove all passwords?").yesText("Remove Passwords").isYes) {
        return
      }

      closeCurrentStore()

      LOG.info("Passwords cleared", Error())
      createKeePassFileManager()?.clear()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getNewDbFile()?.exists() ?: false
    }
  }

  private inner class ImportKeePassDatabaseAction : DumbAwareAction("Import") {
    override fun actionPerformed(event: AnActionEvent) {
      closeCurrentStore()

      FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
        .withFileFilter {
          !it.isDirectory && it.nameSequence.endsWith(".kdbx")
        }
        .chooseFile(event) {
          createKeePassFileManager()?.import(Paths.get(it.path), event)
        }
    }
  }

  private inner class ChangeKeePassDatabaseMasterPasswordAction : DumbAwareAction("${if (MasterKeyFileStorage(getDefaultMasterPasswordFile()).isAutoGenerated()) "Set" else "Change"} Master Password") {
    override fun actionPerformed(event: AnActionEvent) {
      closeCurrentStore()

      // even if current provider is not KEEPASS, all actions for db file must be applied immediately (show error if new master password not applicable for existing db file)
      if (createKeePassFileManager()?.askAndSetMasterKey(event) == true) {
        templatePresentation.text = "Change Master Password"
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getNewDbFileAsString() != null
    }
  }
}

// we must save and close opened KeePass database before any action that can modify KeePass database files
private fun closeCurrentStore() {
  (PasswordSafe.instance as PasswordSafeImpl).closeCurrentStore(isSave = true, isEvenMemoryOnly = false)
}