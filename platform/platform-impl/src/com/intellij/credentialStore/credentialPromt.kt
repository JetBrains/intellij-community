// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CredentialPromptDialog")
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.project.Project
import com.intellij.ui.AppIcon
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize
import javax.swing.JPasswordField

/**
 * @param project The context project (might be null)
 * @param dialogTitle The dialog title
 * @param passwordFieldLabel The password field label, describing a resource, for which password is asked
 * @param resetPassword if true, the old password is removed from database and new password will be asked.
 * @param error The error to show in the dialog
 * @return null if dialog was cancelled or password (stored in database or a entered by user)
 */
@JvmOverloads
fun askPassword(project: Project?,
                dialogTitle: String,
                passwordFieldLabel: String,
                attributes: CredentialAttributes,
                resetPassword: Boolean = false,
                error: String? = null): String? {
  return askCredentials(project, dialogTitle, passwordFieldLabel, attributes,
                        isResetPassword = resetPassword,
                        error = error,
                        isCheckExistingBeforeDialog = true)?.credentials?.getPasswordAsString()?.nullize()
}

@JvmOverloads
fun askCredentials(project: Project?,
                   dialogTitle: String,
                   passwordFieldLabel: String,
                   attributes: CredentialAttributes,
                   isSaveOnOk: Boolean = true,
                   isCheckExistingBeforeDialog: Boolean = false,
                   isResetPassword: Boolean = false,
                   error: String? = null): CredentialRequestResult? {
  val store = PasswordSafe.instance
  if (isResetPassword) {
    store.set(attributes, null)
  }
  else if (isCheckExistingBeforeDialog) {
    store.get(attributes)?.let {
      return CredentialRequestResult(it, false)
    }
  }

  return invokeAndWaitIfNeed(ModalityState.any()) {
    val passwordField = JPasswordField()
    val rememberCheckBox = store.rememberCheckBoxState.createCheckBox(toolTip = "The password will be stored between application sessions.")

    val panel = panel {
      row { label(if (passwordFieldLabel.endsWith(":")) passwordFieldLabel else "$passwordFieldLabel:") }
      row { passwordField() }
      row { rememberCheckBox() }
    }

    AppIcon.getInstance().requestAttention(project, true)
    if (!dialog(dialogTitle, project = project, panel = panel, focusedComponent = passwordField, errorText = error).showAndGet()) {
      return@invokeAndWaitIfNeed null
    }

    store.rememberCheckBoxState.update(rememberCheckBox)

    val credentials = Credentials(attributes.userName, passwordField.password.nullize())
    if (isSaveOnOk && rememberCheckBox.isSelected) {
      store.set(attributes, credentials)
      credentials.getPasswordAsString()
    }

    // for memory only store isRemember is true, because false doesn't matter
    return@invokeAndWaitIfNeed CredentialRequestResult(credentials, isRemember = rememberCheckBox.isSelected)
  }
}

data class CredentialRequestResult(val credentials: Credentials, val isRemember: Boolean)