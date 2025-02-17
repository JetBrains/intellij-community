// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent

class TokenLoginDialog @JvmOverloads constructor(
  project: Project?, parentCs: CoroutineScope, parent: Component?,
  private val model: LoginModel,
  title: @NlsContexts.DialogTitle String = CollaborationToolsBundle.message("login.dialog.title"),
  private val userCustomExitSignal: Flow<Unit>? = null,
  private val centerPanelSupplier: CoroutineScope.() -> DialogPanel
) : DialogWrapper(project, parent, false, IdeModalityType.IDE) {

  @ApiStatus.ScheduledForRemoval
  @Deprecated("A proper coroutine scope should be provided")
  @OptIn(DelicateCoroutinesApi::class)
  @JvmOverloads
  constructor(
    project: Project?, parent: Component?,
    model: LoginModel,
    title: @NlsContexts.DialogTitle String = CollaborationToolsBundle.message("login.dialog.title"),
    userCustomExitSignal: Flow<Unit>? = null,
    centerPanelSupplier: CoroutineScope.() -> DialogPanel
  ) : this(project, GlobalScope, parent, model, title, userCustomExitSignal, centerPanelSupplier)

  private val uiScope = parentCs.childScope(javaClass.name, Dispatchers.EDT + ModalityState.stateForComponent(rootPane).asContextElement())

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

    uiScope.launch {
      userCustomExitSignal?.collectLatest {
        close(NEXT_USER_EXIT_CODE)
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
}