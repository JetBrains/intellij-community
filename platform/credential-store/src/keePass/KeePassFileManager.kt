// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.KeePassDatabase
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

open class KeePassFileManager(private val file: Path,
                              masterKeyFile: Path,
                              private val masterKeyEncryptionSpec: EncryptionSpec,
                              private val secureRandom: Lazy<SecureRandom>) {
  private val masterKeyFileStorage = MasterKeyFileStorage(masterKeyFile)

  fun clear() {
    if (!file.exists()) {
      return
    }

    try {
      // don't create with preloaded empty db because "clear" action should remove only IntelliJ group from database,
      // but don't remove other groups
      val masterPassword = masterKeyFileStorage.load()
      if (masterPassword != null) {
        val db = loadKdbx(file, KdbxPassword.createAndClear(masterPassword))
        val store = KeePassCredentialStore(file, masterKeyFileStorage, db)
        store.clear()
        store.save(masterKeyEncryptionSpec)
        return
      }
    }
    catch (e: Exception) {
      // ok, just remove file
      if (e !is IncorrectMasterPasswordException && ApplicationManager.getApplication()?.isUnitTestMode == false) {
        LOG.error(e)
      }
    }

    file.delete()
  }

  fun import(fromFile: Path, event: AnActionEvent?) {
    if (file == fromFile) {
      return
    }

    try {
      doImportOrUseExisting(fromFile, event)
    }
    catch (e: IncorrectMasterPasswordException) {
      throw e
    }
    catch (e: Exception) {
      LOG.warn(e)
      CredentialStoreUiService.getInstance().showErrorMessage(
        event?.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
        CredentialStoreBundle.message("kee.pass.dialog.title.cannot.import"),
        CredentialStoreBundle.message("kee.pass.dialog.message"))
    }
  }

  // throws IncorrectMasterPasswordException if user cancelled ask master password dialog
  @Throws(IncorrectMasterPasswordException::class)
  fun useExisting() {
    if (file.exists()) {
      if (!doImportOrUseExisting(file, event = null)) {
        throw IncorrectMasterPasswordException()
      }
    }
    else {
      saveDatabase(file, KeePassDatabase(), generateRandomMasterKey(masterKeyEncryptionSpec, secureRandom.value), masterKeyFileStorage,
                   secureRandom.value)
    }
  }

  private fun doImportOrUseExisting(file: Path, event: AnActionEvent?): Boolean {
    val contextComponent = event?.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)

    // check master key file in parent dir of imported file
    val possibleMasterKeyFile = file.parent.resolve(MASTER_KEY_FILE_NAME)
    var masterPassword = MasterKeyFileStorage(possibleMasterKeyFile).load()
    if (masterPassword != null) {
      try {
        loadKdbx(file, KdbxPassword(masterPassword))
      }
      catch (e: IncorrectMasterPasswordException) {
        LOG.warn("On import \"$file\" found existing master key file \"$possibleMasterKeyFile\" but key is not correct")
        masterPassword = null
      }
    }

    if (masterPassword == null && !requestMasterPassword(CredentialStoreBundle.message("kee.pass.dialog.request.master.title"),
                                                         contextComponent = contextComponent) {
        try {
          loadKdbx(file, KdbxPassword(it))
          masterPassword = it
          null
        }
        catch (e: IncorrectMasterPasswordException) {
          CredentialStoreBundle.message("dialog.message.master.password.not.correct")
        }
      }) {
      return false
    }

    if (file !== this.file) {
      Files.copy(file, this.file, StandardCopyOption.REPLACE_EXISTING)
    }
    masterKeyFileStorage.save(createMasterKey(masterPassword!!))
    return true
  }

  fun askAndSetMasterKey(event: AnActionEvent?, @DialogMessage topNote: String? = null): Boolean {
    val contextComponent = event?.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)

    // to open old database, key can be required, so, to avoid showing 2 dialogs, check it before
    val db = try {
      if (file.exists()) loadKdbx(file, KdbxPassword(
        this.masterKeyFileStorage.load() ?: throw IncorrectMasterPasswordException(isFileMissed = true)))
      else KeePassDatabase()
    }
    catch (e: IncorrectMasterPasswordException) {
      // ok, old key is required
      return requestCurrentAndNewKeys(contextComponent)
    }

    return requestMasterPassword(CredentialStoreBundle.message("kee.pass.dialog.title.set.master.password"), topNote = topNote,
                                 contextComponent = contextComponent) {
      saveDatabase(file, db, createMasterKey(it), masterKeyFileStorage, secureRandom.value)
      null
    }
  }

  protected open fun requestCurrentAndNewKeys(contextComponent: Component?): Boolean {
    return CredentialStoreUiService.getInstance().showChangeMasterPasswordDialog(contextComponent, ::doSetNewMasterPassword)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  protected fun doSetNewMasterPassword(current: CharArray, new: CharArray): Boolean {
    val db = loadKdbx(file, KdbxPassword.createAndClear(current.toByteArrayAndClear()))
    saveDatabase(file, db, createMasterKey(new.toByteArrayAndClear()), masterKeyFileStorage, secureRandom.value)
    return false
  }

  private fun createMasterKey(value: ByteArray, isAutoGenerated: Boolean = false) =
    MasterKey(value, isAutoGenerated, masterKeyEncryptionSpec)

  protected open fun requestMasterPassword(@DialogTitle title: String,
                                           @DialogMessage topNote: String? = null,
                                           contextComponent: Component? = null,
                                           @DialogMessage ok: (value: ByteArray) -> String?): Boolean {
    return CredentialStoreUiService.getInstance().showRequestMasterPasswordDialog(title, topNote, contextComponent, ok)
  }

  fun saveMasterKeyToApplyNewEncryptionSpec() {
    // if null, master key file doesn't exist now, it will be saved later somehow, no need to re-save with a new encryption spec
    val existing = masterKeyFileStorage.load() ?: return
    // no need to re-save db file because master password is not changed, only master key encryption spec changed
    masterKeyFileStorage.save(createMasterKey(existing, isAutoGenerated = masterKeyFileStorage.isAutoGenerated()))
  }

  fun setCustomMasterPasswordIfNeeded(defaultDbFile: Path) {
    // https://youtrack.jetbrains.com/issue/IDEA-174581#focus=streamItem-27-3081868-0-0
    // for custom location require to set custom master password to make sure that user will be able to reuse file on another machine
    if (file == defaultDbFile) {
      return
    }

    if (!masterKeyFileStorage.isAutoGenerated()) {
      return
    }

    askAndSetMasterKey(null, topNote = CredentialStoreBundle.message("kee.pass.top.note"))
  }
}
