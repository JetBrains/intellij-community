// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.RememberCheckBoxState
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.text.nullize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.net.URLEncoder
import javax.swing.JPasswordField
import javax.swing.JTextField

internal fun askJBAccountCredentials(parent: Component, project: Project?, authFailed: Boolean = false): Credentials? {
  val prompt = if (authFailed) DiagnosticBundle.message("error.report.auth.failed")
  else DiagnosticBundle.message("error.report.auth.prompt")
  val userField = JTextField()
  val passwordField = JPasswordField()
  lateinit var rememberCheckBox: JBCheckBox

  val panel = panel {
    row {
      text(prompt)
    }
    row(DiagnosticBundle.message("error.report.auth.user")) {
      cell(userField)
        .align(AlignX.FILL)
    }
    row(DiagnosticBundle.message("error.report.auth.pass")) {
      cell(passwordField)
        .align(AlignX.FILL)
    }
    row("") {
      rememberCheckBox = checkBox(UIBundle.message("auth.remember.cb")).component
      link(DiagnosticBundle.message("error.report.auth.restore")) {
        val userName = URLEncoder.encode(userField.text.trim(), Charsets.UTF_8)!!
        BrowserUtil.browse("https://account.jetbrains.com/forgot-password?username=$userName")
      }.align(AlignX.RIGHT)
    }
    row {
      text(DiagnosticBundle.message("error.report.auth.enlist"))
    }
  }
  val decoratorDisposable = Disposer.newDisposable()
  val loadingDecorator = LoadingDecorator(panel, decoratorDisposable, 100, useMinimumSize = true)

  try {
    loadingDecorator.startLoading(false)

    val credentialsJob = service<ITNProxyCoroutineScopeHolder>().coroutineScope.launch {
      val credentials = ErrorReportConfigurable.getCredentials()

      val remember = if (credentials?.userName == null) PasswordSafe.instance.isRememberPasswordByDefault  // EA credentials were never stored
      else !credentials.password.isNullOrEmpty()  // a password was stored already

      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val userName = credentials?.userName
        userField.text = userName
        passwordField.text = credentials?.getPasswordAsString()
        rememberCheckBox.setSelected(remember)
        IdeFocusManager.findInstance().requestFocus(if (userName == null) userField else passwordField, false)
        loadingDecorator.stopLoading()
      }
    }

    try {
      val dialog = dialog(
        title = DiagnosticBundle.message("error.report.auth.title"),
        resizable = true,
        panel = loadingDecorator.component,
        project = project,
        parent = if (parent.isShowing) parent else null)

      if (!dialog.showAndGet()) {
        return null
      }

      val userName = userField.text.nullize(true)
      val password = passwordField.password
      val passwordToRemember = if (rememberCheckBox.isSelected) password else null
      RememberCheckBoxState.update(rememberCheckBox)
      service<ITNProxyCoroutineScopeHolder>().coroutineScope.launch {
        ErrorReportConfigurable.saveCredentials(userName, passwordToRemember)
      }
      return Credentials(userName, password)
    }
    finally {
      credentialsJob.cancel(null)
    }
  }
  finally {
    Disposer.dispose(decoratorDisposable)
  }
}
