// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import java.awt.Component
import javax.swing.JPasswordField

internal val NOTIFICATION_MANAGER by lazy { SingletonNotificationManager("Password Safe", NotificationType.ERROR) }

class CredentialStoreUiServiceImpl : CredentialStoreUiService {
  override fun notify(@NotificationTitle title: String, @NotificationContent content: String, project: Project?, action: NotificationAction?) {
    NOTIFICATION_MANAGER.notify(title, content, project) {
      if (action != null) {
        it.addAction(action)
      }
    }
  }

  override fun showChangeMasterPasswordDialog(contextComponent: Component?,
                                              setNewMasterPassword: (current: CharArray, new: CharArray) -> Boolean): Boolean =
    doShowChangeMasterPasswordDialog(contextComponent, setNewMasterPassword)

  override fun showRequestMasterPasswordDialog(@DialogTitle title: String,
                                               @DialogMessage topNote: String?,
                                               contextComponent: Component?,
                                               @DialogMessage ok: (value: ByteArray) -> String?): Boolean =
    doShowRequestMasterPasswordDialog(title, topNote, contextComponent, ok)

  override fun showErrorMessage(parent: Component?, title: String, message: String) {
    Messages.showErrorDialog(parent, message, title)
  }

  override fun openSettings(project: Project?) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, PasswordSafeConfigurable::class.java)
  }
}

internal fun doShowRequestMasterPasswordDialog(@DialogTitle title: String,
                                               @DialogMessage topNote: String? = null,
                                               contextComponent: Component? = null,
                                               @DialogMessage ok: (value: ByteArray) -> String?): Boolean {
  val passwordField = JPasswordField()
  val panel = panel {
    topNote?.let {
      noteRow(it)
    }
    row(CredentialStoreBundle.message("kee.pass.row.master.password")) { passwordField().focused() }
  }

  return dialog(title = title, panel = panel, parent = contextComponent) {
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
  }.showAndGet()
}

private fun checkIsEmpty(field: JPasswordField, errors: MutableList<ValidationInfo>): CharArray? {
  val chars = field.getTrimmedChars()
  if (chars == null) {
    errors.add(ValidationInfo(CredentialStoreBundle.message("kee.pass.validation.info.current.password.incorrect.current.empty"), field))
  }
  return chars
}

internal fun doShowChangeMasterPasswordDialog(contextComponent: Component?,
                                              setNewMasterPassword: (current: CharArray, new: CharArray) -> Boolean): Boolean {
  val currentPasswordField = JPasswordField()
  val newPasswordField = JPasswordField()
  val panel = com.intellij.ui.dsl.builder.panel {
    row(CredentialStoreBundle.message("kee.pass.row.current.password")) {
      cell(currentPasswordField)
        .columns(COLUMNS_MEDIUM)
        .focused()
    }
    row(CredentialStoreBundle.message("kee.pass.row.new.password")) {
      cell(newPasswordField)
        .columns(COLUMNS_MEDIUM)
    }
    row {
      comment(CredentialStoreBundle.message("kee.pass.row.comment"))
    }
  }

  return dialog(title = CredentialStoreBundle.message("kee.pass.dialog.default.title"), panel = panel, parent = contextComponent) {
    val errors = SmartList<ValidationInfo>()
    val current = checkIsEmpty(currentPasswordField, errors)
    val new = checkIsEmpty(newPasswordField, errors)

    if (errors.isEmpty()) {
      try {
        if (setNewMasterPassword(current!!, new!!)) {
          return@dialog errors
        }
      }
      catch (e: IncorrectMasterPasswordException) {
        errors.add(
          ValidationInfo(CredentialStoreBundle.message("kee.pass.validation.info.current.password.incorrect"), currentPasswordField))
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
