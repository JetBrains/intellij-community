// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.EncryptionSpec
import com.intellij.credentialStore.LOG
import com.intellij.credentialStore.getTrimmedChars
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.toByteArray
import java.awt.Component
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JPasswordField

internal open class KeePassFileManager(private val file: Path, private val masterKeyFile: Path, private val masterKeyEncryptionSpec: EncryptionSpec) {
  fun clear() {
    try {
      val db = KeePassCredentialStore(dbFile = file, masterKeyFile = masterKeyFile)
      db.clear()
      db.save(masterKeyEncryptionSpec)
    }
    catch (e: Exception) {
      // ok, just remove file
      if (e !is IncorrectMasterPasswordException && ApplicationManager.getApplication()?.isUnitTestMode == false) {
        LOG.error(e)
      }
      file.delete()
    }
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
      Messages.showMessageDialog(event?.getData(PlatformDataKeys.CONTEXT_COMPONENT)!!, "Internal error", "Cannot Import", Messages.getErrorIcon())
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
      KeePassCredentialStore(dbFile = file, masterKeyFile = masterKeyFile).save(masterKeyEncryptionSpec)
    }
  }

  private fun doImportOrUseExisting(file: Path, event: AnActionEvent?): Boolean {
    val contextComponent = event?.getData(PlatformDataKeys.CONTEXT_COMPONENT)

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

    if (masterPassword == null && !requestMasterPassword("Specify Master Password", contextComponent) {
        try {
          loadKdbx(file, KdbxPassword(it))
          masterPassword = it
          null
        }
        catch (e: IncorrectMasterPasswordException) {
          "Master password not correct."
        }
      }) {
      return false
    }

    if (file !== this.file) {
      Files.copy(file, this.file, StandardCopyOption.REPLACE_EXISTING)
    }
    MasterKeyFileStorage(masterKeyFile).save(createMasterKey(masterPassword!!))
    return true
  }

  fun askAndSetMasterKey(event: AnActionEvent?): Boolean {
    val contextComponent = event?.getData(PlatformDataKeys.CONTEXT_COMPONENT)

    // to open old database, key can be required, so, to avoid showing 2 dialogs, check it before
    val store = try {
      KeePassCredentialStore(file, masterKeyFile)
    }
    catch (e: IncorrectMasterPasswordException) {
      // ok, old key is required
      return requestCurrentAndNewKeys(contextComponent)
    }

    return requestMasterPassword("Set Master Password", contextComponent) {
      store.setMasterPassword(createMasterKey(it))
      null
    }
  }

  protected open fun requestCurrentAndNewKeys(contextComponent: Component?): Boolean {
    val currentPasswordField = JPasswordField()
    val newPasswordField = JPasswordField()
    val panel = panel {
      //noteRow("Existing KeePass database requires current password.")
      row("Current password:") { currentPasswordField() }
      row("New password:") { newPasswordField() }

      commentRow("If the current password is unknown, clear the KeePass database.")
    }

    return dialog(title = "Change Master Password", panel = panel, focusedComponent = currentPasswordField, parent = contextComponent) {
      val errors = SmartList<ValidationInfo>()
      val current = checkIsEmpty(currentPasswordField, errors)
      val new = checkIsEmpty(newPasswordField, errors)

      if (errors.isEmpty()) {
        try {
          if (doSetNewMasterPassword(current, new)) {
            return@dialog errors
          }
        }
        catch (e: IncorrectMasterPasswordException) {
          errors.add(ValidationInfo("The current password is incorrect.", currentPasswordField))
          new?.fill(0.toChar())
        }
      }
      else {
        current?.fill(0.toChar())
        new?.fill(0.toChar())
      }

      errors
    }.showAndGet()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  protected fun doSetNewMasterPassword(current: CharArray?, new: CharArray?): Boolean {
    val store = KeePassCredentialStore(file, masterKeyFile, preloadedMasterKey = createMasterKey(current!!))
    store.setMasterPassword(createMasterKey(new!!))
    return false
  }

  private fun createMasterKey(value: CharArray) = createMasterKey(value.toByteArrayAndClear())

  private fun createMasterKey(value: ByteArray) = MasterKey(value, isAutoGenerated = false, encryptionSpec = masterKeyEncryptionSpec)

  private fun checkIsEmpty(field: JPasswordField, errors: MutableList<ValidationInfo>): CharArray? {
    val chars = field.getTrimmedChars()
    if (chars == null) {
      errors.add(ValidationInfo("Current master password is empty.", field))
    }
    return chars
  }

  protected open fun requestMasterPassword(title: String, contextComponent: Component?, ok: (value: ByteArray) -> String?): Boolean {
    val passwordField = JPasswordField()
    val panel = panel {
      row("Master password:") { passwordField() }
    }

    return dialog(title = title, panel = panel, focusedComponent = passwordField, parent = contextComponent) {
      val errors = SmartList<ValidationInfo>()
      val value = checkIsEmpty(passwordField, errors)
      if (errors.isEmpty()) {
        val result = value!!.toByteArrayAndClear()
        ok(result)?.let {
          errors.add(ValidationInfo(it, passwordField))
        }
        if (!errors.isEmpty()) {
          result.fill(0)
        }
      }
      errors
    }
      .showAndGet()
  }

  fun saveMasterKeyToApplyNewEncryptionSpec() {
    val masterKeyFileStorage = MasterKeyFileStorage(masterKeyFile)
    // if null, master key file doesn't exist now, it will be saved later somehow, no need to re-save with a new encryption spec
    val existing = masterKeyFileStorage.load() ?: return
    // no need to re-save db file because master password is not changed, only master key encryption spec changed
    masterKeyFileStorage.save(createMasterKey(existing))
  }
}

private fun CharArray.toByteArrayAndClear(): ByteArray {
  val charBuffer = CharBuffer.wrap(this)
  val byteBuffer = Charsets.UTF_8.encode(charBuffer)
  fill(0.toChar())
  return byteBuffer.toByteArray(isClear = true)
}