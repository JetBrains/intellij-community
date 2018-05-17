// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.awt.Component
import javax.swing.JPasswordField
import javax.swing.JTextField

@JvmOverloads
fun showJetBrainsAccountDialog(parent: Component, project: Project? = null): DialogWrapper {
  val credentials = ErrorReportConfigurable.getCredentials()
  val userField = JTextField(credentials?.userName)
  val passwordField = JPasswordField(credentials?.password?.toString())

  val isSelected = if (credentials?.userName == null) {
    // if no user name - never stored and so, defaults
    RememberCheckBoxState.isSelected
  }
  else {
    // if user name set, but no password, so, previously was stored without password
    !credentials.password.isNullOrEmpty()
  }

    credentials?.userName == null || !credentials.password.isNullOrEmpty()
  val rememberCheckBox = CheckBox(CommonBundle.message("checkbox.remember.password"), isSelected)

  val panel = panel {
    noteRow("Login to JetBrains Account to get notified\nwhen the submitted exceptions are fixed.")
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
    noteRow("""Do not have an account? <a href="https://account.jetbrains.com/login?signup">Sign Up</a>""")
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
    PasswordSafe.getInstance().set(CredentialAttributes(ErrorReportConfigurable.SERVICE_NAME, userName), Credentials(userName, password))
    return@dialog null
  }
}