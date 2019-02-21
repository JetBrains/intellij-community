// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.CommonBundle
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.RememberCheckBoxState
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.io.encodeUrlQueryParameter
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
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
  val passwordField = JPasswordField(credentials?.password?.toString())
  val rememberCheckBox = CheckBox(CommonBundle.message("checkbox.remember.password"), remember)

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
    title = DiagnosticBundle.message("error.report.title"),
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

//<editor-fold desc="Deprecated stuff.">
@Deprecated("use #askJBAccountCredentials()")
@ApiStatus.ScheduledForRemoval(inVersion = "2020")
@JvmOverloads
fun showJetBrainsAccountDialog(parent: Component, project: Project? = null): DialogWrapper {
  val credentials = ErrorReportConfigurable.getCredentials()
  val userField = JTextField(credentials?.userName)
  val passwordField = JPasswordField(credentials?.password?.toString())

  val passwordSafe = PasswordSafe.instance
  val isSelected = if (credentials?.userName == null) {
    // if no user name - never stored and so, defaults
    passwordSafe.isRememberPasswordByDefault
  }
  else {
    // if user name set, but no password, so, previously was stored without password
    !credentials.password.isNullOrEmpty()
  }
  val rememberCheckBox = CheckBox(CommonBundle.message("checkbox.remember.password"), isSelected)

  val panel = panel {
    noteRow("Use JetBrains Account to be notified\nwhen reported exceptions are fixed.\n" +
            "Clear user name to submit reports anonymously.")
    row("Username:") { userField(growPolicy = GrowPolicy.SHORT_TEXT) }
    row("Password:") { passwordField() }
    row {
      rememberCheckBox()
      right {
        link("Forgot password?") {
          val userName = userField.text.trim().encodeUrlQueryParameter()
          BrowserUtil.browse("https://account.jetbrains.com/forgot-password?username=$userName")
        }
      }
    }
    noteRow("""Do not have an account yet? <a href="https://account.jetbrains.com/login?signup">Sign Up</a>""")
  }

  return dialog(
      title = DiagnosticBundle.message("error.report.title"),
      panel = panel,
      focusedComponent = if (credentials?.userName == null) userField else passwordField,
      project = project,
      parent = if (parent.isShowing) parent else null) {
    val userName = userField.text.nullize(true)
    val password = if (rememberCheckBox.isSelected) passwordField.password else null
    RememberCheckBoxState.update(rememberCheckBox)
    passwordSafe.set(CredentialAttributes(ErrorReportConfigurable.SERVICE_NAME, userName), Credentials(userName, password))
    return@dialog null
  }
}
//</editor-fold>