// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.RememberCheckBoxState
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.io.encodeUrlQueryParameter
import com.intellij.util.text.nullize
import java.awt.Component
import javax.swing.JPasswordField
import javax.swing.JTextField

@JvmOverloads
fun askJBAccountCredentials(parent: Component, project: Project?, authFailed: Boolean = false): Credentials? {
  val credentials = ErrorReportConfigurable.getCredentials()
  val remember = if (credentials?.userName == null) PasswordSafe.instance.isRememberPasswordByDefault  // EA credentials were never stored
                 else !credentials.password.isNullOrEmpty()  // a password was stored already

  val prompt = if (authFailed) DiagnosticBundle.message("error.report.auth.failed")
               else DiagnosticBundle.message("error.report.auth.prompt")
  val userField = JTextField(credentials?.userName)
  val passwordField = JPasswordField(credentials?.getPasswordAsString())
  val rememberCheckBox = CheckBox(UIBundle.message("auth.remember.cb"), remember)

  val panel = panel {
    noteRow(prompt)
    row(DiagnosticBundle.message("error.report.auth.user")) { userField(growPolicy = GrowPolicy.SHORT_TEXT) }
    row(DiagnosticBundle.message("error.report.auth.pass")) { passwordField() }
    row {
      rememberCheckBox()
      right {
        link(DiagnosticBundle.message("error.report.auth.restore")) {
          val userName = userField.text.trim().encodeUrlQueryParameter()
          BrowserUtil.browse("https://account.jetbrains.com/forgot-password?username=$userName")
        }
      }
    }
    noteRow(DiagnosticBundle.message("error.report.auth.enlist", "https://account.jetbrains.com/login?signup"))
  }

  val dialog = dialog(
    title = DiagnosticBundle.message("error.report.auth.title"),
    panel = panel,
    focusedComponent = if (credentials?.userName == null) userField else passwordField,
    project = project,
    parent = if (parent.isShowing) parent else null)

  if (!dialog.showAndGet()) {
    return null
  }

  val userName = userField.text.nullize(true)
  val password = passwordField.password
  val passwordToRemember = if (rememberCheckBox.isSelected) password else null
  RememberCheckBoxState.update(rememberCheckBox)
  PasswordSafe.instance.set(CredentialAttributes(ErrorReportConfigurable.SERVICE_NAME, userName), Credentials(userName, passwordToRemember))
  return Credentials(userName, password)
}
