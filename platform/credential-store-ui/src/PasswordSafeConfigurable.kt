// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.credentialStore.gpg.Pgp
import com.intellij.credentialStore.gpg.PgpKey
import com.intellij.credentialStore.kdbx.IncorrectMainPasswordException
import com.intellij.credentialStore.keePass.*
import com.intellij.ide.IdeBundle
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.ide.passwordSafe.impl.createPersistentCredentialStore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.chooseFile
import com.intellij.ui.layout.selected
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Paths
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JRadioButton
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

private val LOG: Logger
  get() = logger<PasswordSafeConfigurable>()

/**
 * API note: use [CredentialStoreUiService] instead of using this `.class` to show settings dialog
 */
@ApiStatus.Internal
class PasswordSafeConfigurable : ConfigurableBase<PasswordSafeConfigurableUi, PasswordSafeSettings>("application.passwordSafe",
                                                                                                    CredentialStoreBundle.passwordSafeConfigurable,
                                                                                                    "reference.ide.settings.password.safe") {
  private val settings = service<PasswordSafeSettings>()

  override fun getSettings() = settings

  override fun createUi() = PasswordSafeConfigurableUi(settings)
}

@ApiStatus.Internal
class PasswordSafeConfigurableUi(private val settings: PasswordSafeSettings) : ConfigurableUi<PasswordSafeSettings> {
  private lateinit var panel: DialogPanel
  private lateinit var usePgpKey: JCheckBox
  private lateinit var pgpKeyCombo: ComboBox<PgpKey>
  private lateinit var keepassRadioButton: JRadioButton
  private var keePassDbFile: TextFieldWithBrowseButton? = null

  private val pgpListModel = CollectionComboBoxModel<PgpKey>()

  private val pgp by lazy { Pgp() }

  // https://youtrack.jetbrains.com/issue/IDEA-200188
  // reuse to avoid delays - on Linux SecureRandom is quite slow
  private val secureRandom = lazy { createSecureRandom() }

  override fun reset(settings: PasswordSafeSettings) {
    val secretKeys = pgp.listKeys()
    pgpListModel.replaceAll(secretKeys)
    usePgpKey.text = usePgpKeyText()

    panel.reset()

    keePassDbFile?.text = settings.keepassDb ?: getDefaultDbFile().toString()
  }

  override fun isModified(settings: PasswordSafeSettings): Boolean {
    if (panel.isModified()) return true

    if (keePassDbFile == null) {
      return false
    }
    return isKeepassFileLocationChanged(settings)
  }

  private fun isKeepassFileLocationChanged(settings: PasswordSafeSettings): Boolean {
    return keepassRadioButton.isSelected && getNewDbFileAsString() != settings.keepassDb
  }

  override fun apply(settings: PasswordSafeSettings) {
    val pgpKeyChanged = getNewPgpKey()?.keyId != this.settings.state.pgpKeyId
    val oldProviderType = this.settings.providerType

    panel.apply()
    val providerType = this.settings.providerType

    // close if any, it is more reliable just close current store and later it will be recreated lazily with a new settings
    (PasswordSafe.instance as PasswordSafeImpl).closeCurrentStore(isSave = false, isEvenMemoryOnly = providerType != ProviderType.MEMORY_ONLY)

    val passwordSafe = PasswordSafe.instance as PasswordSafeImpl
    if (oldProviderType != providerType) {
      when (providerType) {
        ProviderType.MEMORY_ONLY -> {
          // nothing else is required to setup
        }

        ProviderType.KEYCHAIN -> {
          // create here to ensure that user will get any error during native store creation
          try {
            val store = createPersistentCredentialStore()
            if (store == null) {
              throw ConfigurationException(IdeBundle.message("settings.password.internal.error.no.available.credential.store.implementation"))
            }
            passwordSafe.currentProvider = store
          }
          catch (e: UnsatisfiedLinkError) {
            LOG.warn(e)
            if (SystemInfo.isLinux) {
              throw ConfigurationException(IdeBundle.message("settings.password.package.libsecret.1.0.is.not.installed"))
            }
            else {
              throw ConfigurationException(e.message)
            }
          }
        }

        ProviderType.KEEPASS -> createAndSaveKeePassDatabaseWithNewOptions(settings)
        else -> throw ConfigurationException(IdeBundle.message("settings.password.unknown.provider.type", providerType))
      }
    }
    else if (isKeepassFileLocationChanged(settings)) {
      createAndSaveKeePassDatabaseWithNewOptions(settings)
    }
    else if (providerType == ProviderType.KEEPASS && pgpKeyChanged) {
      try {
        // not our business in this case, if there is no db file, do not require not null KeePassFileManager
        createKeePassFileManager()?.saveMainKeyToApplyNewEncryptionSpec()
      }
      catch (e: ConfigurationException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error(e)
        throw ConfigurationException(CredentialStoreBundle.message("settings.password.internal.error", e.message ?: e.toString()))
      }
    }

    // not in createAndSaveKeePassDatabaseWithNewOptions (as logically should be) because we want to force users to set custom master passwords even if some another setting (not path) was changed
    // (e.g. PGP key)
    if (providerType == ProviderType.KEEPASS) {
      createKeePassFileManager()?.setCustomMainPasswordIfNeeded(getDefaultDbFile())
    }

    settings.providerType = providerType
  }

  // existing in-memory KeePass database is not used, the same as if switched to KEYCHAIN
  // for KeePass not clear - should we append in-memory credentials to existing database or not
  // (and if database doesn't exist, should we append or not), so, wait first user request (prefer to keep implementation simple)
  private fun createAndSaveKeePassDatabaseWithNewOptions(settings: PasswordSafeSettings) {
    val newDbFile = getNewDbFile() ?: throw ConfigurationException(
      CredentialStoreBundle.message("settings.password.keepass.database.path.is.empty"))
    if (newDbFile.isDirectory()) {
      // we do not normalize as we do on file choose because if user decoded to type path manually,
      // it should be valid path and better to avoid any magic here
      throw ConfigurationException(CredentialStoreBundle.message("settings.password.keepass.database.file.is.directory."))
    }
    if (!newDbFile.fileName.toString().endsWith(".kdbx")) {
      throw ConfigurationException(CredentialStoreBundle.message("settings.password.keepass.database.file.should.ends.with.kdbx"))
    }

    settings.keepassDb = newDbFile.toString()

    try {
      KeePassFileManager(newDbFile, getDefaultMainPasswordFile(), getEncryptionSpec(), secureRandom).useExisting()
    }
    catch (e: IncorrectMainPasswordException) {
      throw ConfigurationException(CredentialStoreBundle.message("settings.password.master.password.for.keepass.database.is.not.correct"))
    }
    catch (e: Exception) {
      LOG.error(e)
      throw ConfigurationException(CredentialStoreBundle.message("settings.password.internal.error", e.message ?: e.toString()))
    }
  }

  private fun getNewDbFile() = getNewDbFileAsString()?.let { Paths.get(it) }

  private fun getNewDbFileAsString() = keePassDbFile!!.text.trim().nullize()

  override fun getComponent(): JPanel {
    panel = panel {
      buttonsGroup(CredentialStoreBundle.message("passwordSafeConfigurable.save.password")) {
        row {
          radioButton(CredentialStoreBundle.message("passwordSafeConfigurable.in.native.keychain"), ProviderType.KEYCHAIN)
        }.visible(CredentialStoreManager.getInstance().isSupported(ProviderType.KEYCHAIN))

        row {
          @Suppress("DialogTitleCapitalization") // KeePass is a proper noun
          keepassRadioButton = radioButton(CredentialStoreBundle.message("passwordSafeConfigurable.in.keepass"), ProviderType.KEEPASS).component
        }.visible(CredentialStoreManager.getInstance().isSupported(ProviderType.KEEPASS))

        indent {
          row(CredentialStoreBundle.message("settings.password.database")) {
            val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
              .withTitle(CredentialStoreBundle.message("passwordSafeConfigurable.keepass.database.file"))
              .withExtensionFilter("kdbx")
            keePassDbFile = textFieldWithBrowseButton(fileChooserDescriptor, fileChosen = {
              val path = if (it.isDirectory) "${it.path}${File.separator}${DB_FILE_NAME}" else it.path
              return@textFieldWithBrowseButton File(path).path
            })
              .resizableColumn()
              .align(AlignX.FILL)
              .gap(RightGap.SMALL)
              .apply {
                if (!SystemInfo.isWindows) comment(CredentialStoreBundle.message("passwordSafeConfigurable.weak.encryption"))
              }.component
            actionsButton(
              ClearKeePassDatabaseAction(),
              ImportKeePassDatabaseAction(),
              ChangeKeePassDatabaseMasterPasswordAction()
            )
          }
          row {
            usePgpKey = checkBox(usePgpKeyText())
              .bindSelected({ !pgpListModel.isEmpty && settings.state.pgpKeyId != null },
                            { if (!it) settings.state.pgpKeyId = null })
              .gap(RightGap.SMALL)
              .component

            pgpKeyCombo = comboBox<PgpKey>(pgpListModel, renderer = textListCellRenderer {
              it?.let { "${it.userId} (${it.keyId})" }
            }).bindItem({ getSelectedPgpKey() ?: pgpListModel.items.firstOrNull() },
                        { settings.state.pgpKeyId = if (usePgpKey.isSelected) it?.keyId else null })
              .columns(COLUMNS_MEDIUM)
              .enabledIf(usePgpKey.selected)
              .component
          }
        }
          .enabledIf(keepassRadioButton.selected)
          .visible(CredentialStoreManager.getInstance().isSupported(ProviderType.KEEPASS))

        row {
          radioButton(CredentialStoreBundle.message("passwordSafeConfigurable.do.not.save"), ProviderType.MEMORY_ONLY)
        }.visible(CredentialStoreManager.getInstance().isSupported(ProviderType.MEMORY_ONLY))

      }.bind(settings::providerType)
    }
    return panel
  }

  @NlsContexts.Checkbox
  private fun usePgpKeyText(): String {
    return if (pgpListModel.isEmpty) CredentialStoreBundle.message("passwordSafeConfigurable.protect.master.password.using.pgp.key.no.keys")
    else CredentialStoreBundle.message("passwordSafeConfigurable.protect.master.password.using.pgp.key")
  }

  private fun getSelectedPgpKey(): PgpKey? {
    val currentKeyId = settings.state.pgpKeyId ?: return null
    return (pgpListModel.items.firstOrNull { it.keyId == currentKeyId })
           ?: pgpListModel.items.firstOrNull()
  }

  private fun createKeePassFileManager(): KeePassFileManager? {
    return KeePassFileManager(getNewDbFile() ?: return null, getDefaultMainPasswordFile(), getEncryptionSpec(), secureRandom)
  }

  private fun getEncryptionSpec(): EncryptionSpec {
    return when (val pgpKey = getNewPgpKey()) {
      null -> EncryptionSpec(type = getDefaultEncryptionType(), pgpKeyId = null)
      else -> EncryptionSpec(type = EncryptionType.PGP_KEY, pgpKeyId = pgpKey.keyId)
    }
  }

  private fun getNewPgpKey() = pgpKeyCombo.selectedItem as? PgpKey

  private inner class ClearKeePassDatabaseAction : DumbAwareAction(CredentialStoreBundle.message("action.text.password.safe.clear")) {
    override fun actionPerformed(event: AnActionEvent) {
      if (!MessageDialogBuilder.yesNo(CredentialStoreBundle.message("passwordSafeConfigurable.clear.passwords"),
                                      CredentialStoreBundle.message("passwordSafeConfigurable.are.you.sure")).yesText(
          CredentialStoreBundle.message("passwordSafeConfigurable.remove.passwords")).ask(event.project)) {
        return
      }

      closeCurrentStore()

      LOG.info("Passwords cleared", Error())
      createKeePassFileManager()?.clear()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getNewDbFile()?.exists() ?: false
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private inner class ImportKeePassDatabaseAction : DumbAwareAction(CredentialStoreBundle.message("action.text.password.safe.import")) {
    override fun actionPerformed(event: AnActionEvent) {
      closeCurrentStore()

      FileChooserDescriptorFactory.createSingleFileDescriptor()
        .withExtensionFilter("kdbx")
        .chooseFile(event) {
          createKeePassFileManager()?.import(Paths.get(it.path), event)
        }
    }
  }

  private inner class ChangeKeePassDatabaseMasterPasswordAction : DumbAwareAction(
    if (MainKeyFileStorage(getDefaultMainPasswordFile()).isAutoGenerated()) CredentialStoreBundle.message("action.set.password.text")
    else CredentialStoreBundle.message("action.change.password.text")
  ) {
    override fun actionPerformed(event: AnActionEvent) {
      closeCurrentStore()

      // even if current provider is not KEEPASS, all actions for db file must be applied immediately (show error if new master password not applicable for existing db file)
      if (createKeePassFileManager()?.askAndSetMainKey(event) == true) {
        templatePresentation.text = CredentialStoreBundle.message("settings.password.change.master.password")
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getNewDbFileAsString() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }
}

// we must save and close opened KeePass database before any action that can modify KeePass database files
private fun closeCurrentStore() {
  (PasswordSafe.instance as PasswordSafeImpl).closeCurrentStore(isSave = true, isEvenMemoryOnly = false)
}
