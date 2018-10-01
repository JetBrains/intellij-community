// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.KeePassCredentialStore
import com.intellij.credentialStore.LOG
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.credentialStore.kdbx.KdbxPassword
import com.intellij.credentialStore.kdbx.loadKdbx
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.util.io.delete
import com.intellij.util.io.toByteArray
import java.awt.Component
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


internal class KeePassFileManager(private val file: Path, private val masterKeyFile: Path) {
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
    val masterPassword = requestMasterPassword(contextComponent, "Specify Master Password") ?: return
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

  fun setMasterKey(masterKey: MasterKey) {
    val store: KeePassCredentialStore
    try {
      store = KeePassCredentialStore(file, masterKeyFile)
    }
    catch (e: IncorrectMasterPasswordException) {
      // maybe new master key it is current (and stored in master key file is not correct)?
      try {
        KeePassCredentialStore(file, masterKeyFile, preloadedMasterKey = masterKey)
        // no exception - is set correctly (if preloadedMasterKey specified, KeePassCredentialStore will automatically save it to master key file)
        return
      }
      catch (e: IncorrectMasterPasswordException) {
        // ok... let's ask user about old master password for existing database
        throw TODO()
      }
    }

    store.setMasterPassword(masterKey)
  }
}

internal fun requestMasterPassword(contextComponent: Component, title: String): ByteArray? {
  return MessagesService.getInstance().showPasswordDialog(contextComponent, "Master Password:", title, null, null)?.toByteArrayAndClear()
}

private fun CharArray.toByteArrayAndClear(): ByteArray {
  val charBuffer = CharBuffer.wrap(this)
  val byteBuffer = Charsets.UTF_8.encode(charBuffer)
  charBuffer.array().fill(0.toChar())
  return byteBuffer.toByteArray(isClear = true)
}