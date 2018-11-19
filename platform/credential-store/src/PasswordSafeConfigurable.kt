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
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import com.intellij.util.text.nullize
import java.io.File
import java.nio.file.Paths
import javax.swing.JPanel

internal class PasswordSafeConfigurable(private val settings: PasswordSafeSettings) : ConfigurableBase<PasswordSafeConfigurableUi, PasswordSafeSettings>("application.passwordSafe",
                                                                                                                                                         "Passwords",
                                                                                                                                                         "reference.ide.settings.password.safe") {
  override fun getSettings() = settings

  override fun createUi() = PasswordSafeConfigurableUi()
}

internal class PasswordSafeConfigurableUi : ConfigurableUi<PasswordSafeSettings> {
  private var keePassDbFile: TextFieldWithBrowseButton? = null

  private var isUsePgp = BooleanPropertyWithComboBoxUiManager(CollectionComboBoxModel<PgpKey>())
  private val providerTypeModel = ChoicePropertyUiManager(ProviderType.KEYCHAIN)

  private val pgp by lazy { Pgp() }

  // https://youtrack.jetbrains.com/issue/IDEA-200188
  // reuse to avoid delays - on Linux SecureRandom is quite slow
  private val secureRandom = lazy { createSecureRandom() }

  override fun reset(settings: PasswordSafeSettings) {
    providerTypeModel.selected = settings.providerType

    @Suppress("IfThenToElvis")
    keePassDbFile?.text = settings.keepassDb ?: getDefaultKeePassDbFile().toString()

    val secretKeys = pgp.listKeys()
    isUsePgp.listModel.replaceAll(secretKeys)

    val currentKeyId = settings.state.pgpKeyId
    isUsePgp.selected = (if (currentKeyId == null) null else secretKeys.firstOrNull { it.keyId == currentKeyId }) ?: secretKeys.firstOrNull()
    isUsePgp.value = !secretKeys.isEmpty() && currentKeyId != null
    isUsePgp.isEnabled = providerTypeModel.selected == ProviderType.KEEPASS && !secretKeys.isEmpty()
  }

  override fun isModified(settings: PasswordSafeSettings): Boolean {
    if (keePassDbFile == null) {
      return false
    }
    return getNewProviderType() != settings.providerType || isKeepassFileLocationChanged(settings) || isPgpKeyChanged(settings)
  }

  private fun isPgpKeyChanged(settings: PasswordSafeSettings) = settings.state.pgpKeyId != getNewPgpKey()?.keyId

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
      try {
        // not our business in this case, if there is no db file, do not require not null KeePassFileManager
        createKeePassFileManager()?.saveMasterKeyToApplyNewEncryptionSpec()
      }
      catch (e: ConfigurationException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error(e)
        throw ConfigurationException("Internal error: ${e.message}")
      }
    }

    // not in createAndSaveKeePassDatabaseWithNewOptions (as logically should be) because we want to force users to set custom master passwords even if some another setting (not path) was changed
    // (e.g. PGP key)
    if (providerType == ProviderType.KEEPASS) {
      createKeePassFileManager()?.setCustomMasterPasswordIfNeed(getDefaultKeePassDbFile())
    }

    settings.providerType = providerType
    settings.state.pgpKeyId = getNewPgpKey()?.keyId
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

  override fun getComponent(): JPanel {
    return panel {
      row { label("Save passwords:") }

      buttonGroup(providerTypeModel) {
        if (SystemInfo.isLinux || isMacOsCredentialStoreSupported) {
          row {
            radioButton("In native Keychain", ProviderType.KEYCHAIN)
          }
        }

        row {
          radioButton("In KeePass", ProviderType.KEEPASS)
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
          row {
            cell {
              val prefix = "Protect master password using PGP key"
              checkBox(if (isUsePgp.listModel.isEmpty) "$prefix (No keys configured)" else "$prefix:", propertyUiManager = isUsePgp)
              comboBox(isUsePgp, growPolicy = GrowPolicy.MEDIUM_TEXT) { value, _, _ ->
                setText("${value.userId} (${value.keyId})")
              }
            }
          }
        }
        row {
          radioButton("Do not save, forget passwords after restart", ProviderType.MEMORY_ONLY)
        }
      }
    }
  }

  private fun createKeePassFileManager(): KeePassFileManager? {
    return KeePassFileManager(getNewDbFile() ?: return null, getDefaultMasterPasswordFile(), getEncryptionSpec(), secureRandom)
  }

  private fun getEncryptionSpec(): EncryptionSpec {
    val pgpKey = getNewPgpKey()
    return when (pgpKey) {
      null -> EncryptionSpec(type = getDefaultEncryptionType(), pgpKeyId = null)
      else -> EncryptionSpec(type = EncryptionType.PGP_KEY, pgpKeyId = pgpKey.keyId)
    }
  }

  private fun getNewPgpKey() = isUsePgp.selected

  private fun getNewProviderType() = providerTypeModel.selected

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