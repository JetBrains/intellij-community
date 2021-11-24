// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.RememberCheckBoxState
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.EMPTY_LABEL
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
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
  lateinit var rememberCheckBox: JBCheckBox

  val panel = panel {
    row {
      text(prompt)
    }
    row(DiagnosticBundle.message("error.report.auth.user")) {
      cell(userField)
        .horizontalAlign(HorizontalAlign.FILL)
    }
    row(DiagnosticBundle.message("error.report.auth.pass")) {
      cell(passwordField)
        .horizontalAlign(HorizontalAlign.FILL)
    }
    row(EMPTY_LABEL) {
      rememberCheckBox = checkBox(UIBundle.message("auth.remember.cb"))
        .applyToComponent { isSelected = remember }
        .component
      link(DiagnosticBundle.message("error.report.auth.restore")) {
        val userName = userField.text.trim().encodeUrlQueryParameter()
        BrowserUtil.browse("https://account.jetbrains.com/forgot-password?username=$userName")
      }.horizontalAlign(HorizontalAlign.RIGHT)
    }
    row {
      text(DiagnosticBundle.message("error.report.auth.enlist"))
    }
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
  ErrorReportConfigurable.saveCredentials(userName, passwordToRemember)
  return Credentials(userName, password)
}
