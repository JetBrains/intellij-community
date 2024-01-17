// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.awt.Component
import javax.swing.JComponent

class TokenLoginDialog(
  project: Project?, parent: Component?,
  private val model: LoginModel,
  @NlsContexts.DialogTitle title: String = CollaborationToolsBundle.message("login.dialog.title"),
  private val centerPanelSupplier: CoroutineScope.() -> DialogPanel
) : DialogWrapper(project, parent, false, IdeModalityType.IDE) {

  private val uiScope = DisposingMainScope(disposable) + ModalityState.stateForComponent(rootPane).asContextElement()

  init {
    setOKButtonText(CollaborationToolsBundle.message("login.button"))
    setTitle(title)
    init()

    uiScope.launch {
      model.loginState.collectLatest { state ->
        isOKActionEnabled = state !is LoginModel.LoginState.Connecting

        if (state is LoginModel.LoginState.Failed) startTrackingValidation()

        if (state is LoginModel.LoginState.Connected) close(OK_EXIT_CODE)
      }
    }
  }

  override fun createCenterPanel(): JComponent = uiScope.centerPanelSupplier()

  override fun doOKAction() {
    applyFields()
    if (!isOKActionEnabled) return

    uiScope.launch {
      model.login()
      initValidation()
    }
  }

  override fun doValidate(): ValidationInfo? {
    val loginState = model.loginState.value
    if (loginState is LoginModel.LoginState.Failed) {
      val errorMessage = ExceptionUtil.getPresentableMessage(loginState.error)
      return ValidationInfo(errorMessage).withOKEnabled()
    }
    return null
  }
}