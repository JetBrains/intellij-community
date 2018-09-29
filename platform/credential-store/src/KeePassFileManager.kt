// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

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
import com.intellij.util.text.nullize
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class KeePassFileManager(private val file: Path, private val masterPasswordFile: Path) {
  fun clear(pendingMasterPassword: ByteArray?) {
    try {
      val db = KeePassCredentialStore(dbFile = file, masterPasswordFile = masterPasswordFile, preloadedMasterPassword = pendingMasterPassword)
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
    passwordSafe.currentProvider = KeePassCredentialStore(dbFile = file, masterPasswordFile = masterPasswordFile, preloadedDb = database, preloadedMasterPassword = masterPassword)
  }
}

internal fun requestMasterPassword(contextComponent: Component, title: String): ByteArray? {
  return MessagesService.getInstance().showPasswordDialog(contextComponent, "Master Password:", title, null, null)?.trim().nullize()?.toByteArray()
}
