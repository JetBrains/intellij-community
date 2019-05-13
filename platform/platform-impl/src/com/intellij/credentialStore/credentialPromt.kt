// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CredentialPromptDialog")
package com.intellij.credentialStore

import com.intellij.CommonBundle
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.ui.AppIcon
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize
import javax.swing.JCheckBox
import javax.swing.JPasswordField
import javax.swing.text.BadLocationException
import javax.swing.text.Segment

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

  return invokeAndWaitIfNeeded(ModalityState.any()) {
    val passwordField = JPasswordField()
    val rememberCheckBox = RememberCheckBoxState.createCheckBox(toolTip = "The password will be stored between application sessions.")

    val panel = panel {
      row { label(if (passwordFieldLabel.endsWith(":")) passwordFieldLabel else "$passwordFieldLabel:") }
      row { passwordField() }
      row { rememberCheckBox() }
    }

    AppIcon.getInstance().requestAttention(project, true)
    if (!dialog(dialogTitle, project = project, panel = panel, focusedComponent = passwordField, errorText = error).showAndGet()) {
      return@invokeAndWaitIfNeeded null
    }

    RememberCheckBoxState.update(rememberCheckBox)

    val credentials = Credentials(attributes.userName, passwordField.getTrimmedChars())
    if (isSaveOnOk && rememberCheckBox.isSelected) {
      store.set(attributes, credentials)
      credentials.getPasswordAsString()
    }

    // for memory only store isRemember is true, because false doesn't matter
    return@invokeAndWaitIfNeeded CredentialRequestResult(credentials, isRemember = rememberCheckBox.isSelected)
  }
}

data class CredentialRequestResult(val credentials: Credentials, val isRemember: Boolean)

object RememberCheckBoxState {
  val isSelected: Boolean
    get() = PasswordSafe.instance.isRememberPasswordByDefault

  @JvmStatic
  fun update(component: JCheckBox) {
    PasswordSafe.instance.isRememberPasswordByDefault = component.isSelected
  }

  fun createCheckBox(toolTip: String?): JCheckBox {
    return CheckBox(
      CommonBundle.message("checkbox.remember.password"),
      selected = isSelected,
      toolTip = toolTip
    )
  }
}

// do not trim trailing whitespace
fun JPasswordField.getTrimmedChars(): CharArray? {
  val doc = document
  val size = doc.length
  if (size == 0) {
     return null
  }

  val segment = Segment()
  try {
    doc.getText(0, size, segment)
  }
  catch (e: BadLocationException) {
    return null
  }

  val chars = segment.array
  var startOffset = segment.offset
  while (Character.isWhitespace(chars[startOffset])) {
    startOffset++
  }
  // exclusive
  var endIndex = segment.count
  while (endIndex > startOffset && Character.isWhitespace(chars[endIndex - 1])) {
    endIndex--
  }

  if (startOffset >= endIndex) {
    return null
  }
  else if (startOffset == 0 && endIndex == chars.size) {
    return chars
  }

  val result = chars.copyOfRange(startOffset, endIndex)
  chars.fill(0.toChar(), segment.offset, segment.count)
  return result
}