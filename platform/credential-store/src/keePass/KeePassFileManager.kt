// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.IncorrectMainPasswordException
import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.KeePassDatabase
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.util.io.delete
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import kotlin.io.path.exists

open class KeePassFileManager(private val file: Path,
                              mainKeyFile: Path,
                              private val mainKeyEncryptionSpec: EncryptionSpec,
                              private val secureRandom: Lazy<SecureRandom>) {
  private val mainKeyFileStorage = MainKeyFileStorage(mainKeyFile)

  fun clear() {
    if (!file.exists()) {
      return
    }

    try {
      // don't create with preloaded empty db because "clear" action should remove only IntelliJ group from a database,
      // but don't remove other groups
      val mainPassword = mainKeyFileStorage.load()
      if (mainPassword != null) {
        val db = loadKdbx(file, KdbxPassword.createAndClear(mainPassword))
        val store = KeePassCredentialStore(file, mainKeyFileStorage, db)
        store.clear()
        store.save(mainKeyEncryptionSpec)
        return
      }
    }
    catch (e: Exception) {
      // ok, just remove file
      if (e !is IncorrectMainPasswordException && ApplicationManager.getApplication()?.isUnitTestMode == false) {
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
    catch (e: IncorrectMainPasswordException) {
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

  // throws IncorrectMainPasswordException if user cancelled ask the main password dialog
  @Throws(IncorrectMainPasswordException::class)
  fun useExisting() {
    if (file.exists()) {
      if (!doImportOrUseExisting(file, event = null)) {
        throw IncorrectMainPasswordException()
      }
    }
    else {
      saveDatabase(file, KeePassDatabase(), generateRandomMainKey(mainKeyEncryptionSpec, secureRandom.value), mainKeyFileStorage,
                   secureRandom.value)
    }
  }

  private fun doImportOrUseExisting(file: Path, event: AnActionEvent?): Boolean {
    val contextComponent = event?.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)

    // check the main key file in parent dir of imported file
    val possibleMainKeyFile = file.parent.resolve(MAIN_KEY_FILE_NAME)
    var mainPassword = MainKeyFileStorage(possibleMainKeyFile).load()
    if (mainPassword != null) {
      try {
        loadKdbx(file, KdbxPassword(mainPassword))
      }
      catch (e: IncorrectMainPasswordException) {
        LOG.warn("On import \"$file\" found existing main key file \"$possibleMainKeyFile\" but key is not correct")
        mainPassword = null
      }
    }

    if (mainPassword == null && !requestMainPassword(CredentialStoreBundle.message("kee.pass.dialog.request.main.title"),
                                                     contextComponent = contextComponent) {
        try {
          loadKdbx(file, KdbxPassword(it))
          mainPassword = it
          null
        }
        catch (e: IncorrectMainPasswordException) {
          CredentialStoreBundle.message("dialog.message.main.password.not.correct")
        }
      }) {
      return false
    }

    if (file !== this.file) {
      Files.copy(file, this.file, StandardCopyOption.REPLACE_EXISTING)
    }
    mainKeyFileStorage.save(createMainKey(mainPassword!!))
    return true
  }

  fun askAndSetMainKey(event: AnActionEvent?, @DialogMessage topNote: String? = null): Boolean {
    val contextComponent = event?.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)

    // to open an old database, key can be required, so, to avoid showing 2 dialogs, check it before
    val db = try {
      if (file.exists()) loadKdbx(file, KdbxPassword(
        this.mainKeyFileStorage.load() ?: throw IncorrectMainPasswordException(isFileMissed = true)))
      else KeePassDatabase()
    }
    catch (e: IncorrectMainPasswordException) {
      // ok, old key is required
      return requestCurrentAndNewKeys(contextComponent)
    }

    return requestMainPassword(title = CredentialStoreBundle.message("kee.pass.dialog.title.set.main.password"),
                               topNote = topNote,
                               contextComponent = contextComponent) {
      saveDatabase(file, db, createMainKey(it), mainKeyFileStorage, secureRandom.value)
      null
    }
  }

  protected open fun requestCurrentAndNewKeys(contextComponent: Component?): Boolean {
    return CredentialStoreUiService.getInstance().showChangeMainPasswordDialog(contextComponent, ::doSetNewMainPassword)
  }

  protected fun doSetNewMainPassword(current: CharArray, new: CharArray): Boolean {
    val db = loadKdbx(file = file, credentials = KdbxPassword.createAndClear(current.toByteArrayAndClear()))
    saveDatabase(dbFile = file,
                 db = db,
                 mainKey = createMainKey(new.toByteArrayAndClear()),
                 mainKeyStorage = mainKeyFileStorage,
                 secureRandom = secureRandom.value)
    return false
  }

  private fun createMainKey(value: ByteArray, isAutoGenerated: Boolean = false): MainKey {
    return MainKey(value, isAutoGenerated, mainKeyEncryptionSpec)
  }

  protected open fun requestMainPassword(@DialogTitle title: String,
                                         @DialogMessage topNote: String? = null,
                                         contextComponent: Component? = null,
                                         @DialogMessage ok: (value: ByteArray) -> String?): Boolean {
    return CredentialStoreUiService.getInstance().showRequestMainPasswordDialog(title, topNote, contextComponent, ok)
  }

  fun saveMainKeyToApplyNewEncryptionSpec() {
    // if null, the main key file doesn't exist now, it will be saved later somehow, no need to re-save with a new encryption spec
    val existing = mainKeyFileStorage.load() ?: return
    // no need to re-save db file because the main password is not changed, only the main key encryption spec changed
    mainKeyFileStorage.save(createMainKey(existing, isAutoGenerated = mainKeyFileStorage.isAutoGenerated()))
  }

  fun setCustomMainPasswordIfNeeded(defaultDbFile: Path) {
    // https://youtrack.jetbrains.com/issue/IDEA-174581#focus=streamItem-27-3081868-0-0
    // for custom location require
    // to set the custom main password to make sure that user will be able to reuse file on another machine
    if (file == defaultDbFile) {
      return
    }

    if (!mainKeyFileStorage.isAutoGenerated()) {
      return
    }

    askAndSetMainKey(null, topNote = CredentialStoreBundle.message("kee.pass.top.note"))
  }
}
