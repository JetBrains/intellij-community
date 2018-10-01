// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.KeePassCredentialStore
import com.intellij.credentialStore.LOG
import com.intellij.credentialStore.getTrimmedChars
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import com.intellij.util.io.delete
import com.intellij.util.io.toByteArray
import java.awt.Component
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JPasswordField

internal open class KeePassFileManager(private val file: Path, private val masterKeyFile: Path) {
  fun clear() {
    try {
      val db = KeePassCredentialStore(dbFile = file, masterKeyFile = masterKeyFile)
      db.clear()
      db.save()
    }
    catch (e: Exception) {
      // ok, just remove file
      if (ApplicationManager.getApplication()?.isUnitTestMode == false) {
        LOG.error(e)
      }
      file.delete()
    }
  }

  fun importKeepassFile(fromFile: Path, event: AnActionEvent, passwordSafe: PasswordSafeImpl) {
    if (file == fromFile) {
      return
    }

    val contextComponent = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) as Component
    val masterPassword = requestMasterPassword("Specify Master Password", contextComponent) ?: return
    val database = try {
      loadKdbx(fromFile, KdbxPassword(masterPassword))
    }
    catch (e: Exception) {
      val message: String
      when (e) {
        is IncorrectMasterPasswordException -> {
          message = "Master password is not correct"
          LOG.debug(e)
        }
        else -> {
          message = "Internal error"
          LOG.error(e)
        }
      }
      Messages.showMessageDialog(contextComponent, message, "Cannot Import", Messages.getErrorIcon())
      return
    }

    Files.copy(fromFile, file, StandardCopyOption.REPLACE_EXISTING)
    passwordSafe.currentProvider = KeePassCredentialStore(dbFile = file, masterKeyFile = masterKeyFile, preloadedDb = database,
                                                          preloadedMasterKey = MasterKey(masterPassword))
  }

  fun askAndSetMasterKey(event: AnActionEvent?) {
    val contextComponent = event?.getData(PlatformDataKeys.CONTEXT_COMPONENT)

    // to open old database, key can be required, so, to avoid showing 2 dialogs, check it before
    val store = try {
      KeePassCredentialStore(file, masterKeyFile)
    }
    catch (e: IncorrectMasterPasswordException) {
      // ok, old key is required
      requestOldAndNewKeys(contextComponent)
      return
    }
    store.setMasterPassword(MasterKey(requestMasterPassword("Set Master Password", contextComponent) ?: return))
  }

  protected open fun requestOldAndNewKeys(contextComponent: Component?) {
    val oldPasswordField = JPasswordField()
    val newPasswordField = JPasswordField()
    val panel = panel {
      noteRow("Existing KeePass database requires old password.")
      row("Old password:") { oldPasswordField() }
      row("New password:") { newPasswordField() }

      noteRow("If you don't remember old password,\nthe only solution to Clear database.")
    }

    dialog(title = "Change Master Password", panel = panel, focusedComponent = oldPasswordField, parent = contextComponent) {
      val errors = SmartList<ValidationInfo>()
      val old = checkIsEmpty(oldPasswordField, errors)
      val new = checkIsEmpty(newPasswordField, errors)

      if (errors.isEmpty()) {
        try {
          if (doSetNewMasterPassword(old, new)) {
            return@dialog errors
          }
        }
        catch (e: IncorrectMasterPasswordException) {
          errors.add(ValidationInfo("Old password not correct", oldPasswordField))
          new?.fill(0.toChar())
        }
      }
      else {
        old?.fill(0.toChar())
        new?.fill(0.toChar())
      }

      errors
    }.show()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  protected fun doSetNewMasterPassword(old: CharArray?, new: CharArray?): Boolean {
    val store = KeePassCredentialStore(file, masterKeyFile, preloadedMasterKey = MasterKey(old!!.toByteArrayAndClear()))
    store.setMasterPassword(MasterKey(new!!.toByteArrayAndClear()))
    return false
  }

  private fun checkIsEmpty(field: JPasswordField, errors: MutableList<ValidationInfo>): CharArray? {
    val chars = field.getTrimmedChars()
    if (chars == null) {
      errors.add(ValidationInfo("Old master password is empty.", field))
    }
    return chars
  }

  protected open fun requestMasterPassword(title: String, contextComponent: Component?): ByteArray? {
    val passwordField = JPasswordField()
    val panel = panel {
      row("Master password:") { passwordField() }
    }
    if (dialog(title = title, panel = panel, focusedComponent = passwordField, parent = contextComponent).showAndGet()) {
      return passwordField.getTrimmedChars()?.toByteArrayAndClear()
    }
    else {
      return null
    }
  }
}

private fun CharArray.toByteArrayAndClear(): ByteArray {
  val charBuffer = CharBuffer.wrap(this)
  val byteBuffer = Charsets.UTF_8.encode(charBuffer)
  charBuffer.array().fill(0.toChar())
  return byteBuffer.toByteArray(isClear = true)
}