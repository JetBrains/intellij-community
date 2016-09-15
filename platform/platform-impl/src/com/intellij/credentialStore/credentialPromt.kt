/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("CredentialPromptDialog")
package com.intellij.credentialStore

import com.intellij.CommonBundle
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.project.Project
import com.intellij.ui.components.CheckBox
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
  val store = PasswordSafe.getInstance()
  if (resetPassword) {
    store.set(attributes, null)
  }
  else {
    store.get(attributes)?.getPasswordAsString()?.nullize()?.let {
      return it
    }
  }

  return invokeAndWaitIfNeed(ModalityState.any()) {
    val passwordField = JPasswordField()
    val rememberCheckBox = if (store.isMemoryOnly) {
      null
    }
    else {
      CheckBox(CommonBundle.message("checkbox.remember.password"),
               selected = true,
               toolTip = "The password will be stored between application sessions.")
    }

    val panel = panel {
      row { label(if (passwordFieldLabel.endsWith(":")) passwordFieldLabel else "$passwordFieldLabel:") }
      row { passwordField() }
      rememberCheckBox?.let {
        row { it() }
      }
    }

    if (dialog(dialogTitle, project = project, panel = panel, focusedComponent = passwordField, errorText = error).showAndGet()) {
      val credentials = Credentials(attributes.userName, passwordField.password.nullize())
      store.set(attributes, credentials, store.isMemoryOnly || rememberCheckBox!!.isSelected)
      credentials.getPasswordAsString()
    }
    else {
      null
    }
  }
}