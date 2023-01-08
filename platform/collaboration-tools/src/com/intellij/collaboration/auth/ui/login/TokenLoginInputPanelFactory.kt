// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.swing.event.DocumentEvent

/**
 * Basic token login interface with validation and progress indication
 *
 * To save data in [model] one should call [DialogPanel.apply]
 */
class TokenLoginInputPanelFactory(
  private val model: TokenLoginPanelModel
) {

  fun create(serverFieldDisabled: Boolean, tokenNote: @NlsContexts.DetailedDescription String?): DialogPanel {

    val serverTextField = ExtendableTextField()
    val progressExtension = ExtendableTextComponent.Extension
      .create(AnimatedIcon.Default(), CollaborationToolsBundle.message("login.progress"), null)

    val progressModel = SingleValueModel(false).also {
      it.addAndInvokeListener { inProgress ->
        if (inProgress) serverTextField.addExtension(progressExtension)
        else serverTextField.removeExtension(progressExtension)
      }
    }

    return panel {
      row(CollaborationToolsBundle.message("login.field.server")) {
        cell(serverTextField)
          .bindText(model::serverUri)
          .align(AlignX.FILL)
          .resizableColumn()
          .comment(tokenNote)
          .enabledIf(progressModel.toComponentPredicate(!serverFieldDisabled))
          .validationOnApply {
            when {
              it.text.isBlank() -> error(CollaborationToolsBundle.message("login.server.empty"))
              !isValidServerUri(it.text) -> error(CollaborationToolsBundle.message("login.server.invalid"))
              else -> null
            }
          }
      }
      row(CollaborationToolsBundle.message("login.field.token")) {
        val tokenField = textField()
          .bindText(model::token)
          .align(AlignX.FILL)
          .resizableColumn()
          .enabledIf(progressModel.toComponentPredicate())
          .validationOnApply {
            when {
              it.text.isBlank() -> error(CollaborationToolsBundle.message("login.token.empty"))
              else -> null
            }
          }
          .focused()
          .component

        if (model is LoginTokenGenerator) {
          button(CollaborationToolsBundle.message("login.token.generate")) {
            val token = model.generateToken(serverTextField.text)
            if (token != null) tokenField.text = token
            IdeFocusManager.findInstanceByComponent(tokenField).requestFocus(tokenField, false)
          }.enabledIf(TokenGeneratorPredicate(model, serverTextField))
        }
      }
    }.withPreferredWidth(350).apply {
      // need to force update server field
      reset()
    }.also { panel ->
      UiNotifyConnector(panel, CoroutineActivatable {
        model.loginState.collectLatest { state ->
          progressModel.value = state is LoginModel.LoginState.Connecting
        }
      }, false)
    }
  }

  companion object {
    private fun isValidServerUri(uri: String): Boolean {
      return try {
        URIUtil.normalizeAndValidateHttpUri(uri)
        true
      }
      catch (e: Throwable) {
        false
      }
    }

    private fun SingleValueModel<Boolean>.toComponentPredicate(defaultState: Boolean = true) = object : ComponentPredicate() {
      override fun addListener(listener: (Boolean) -> Unit) {
        this@toComponentPredicate.addListener {
          listener(!it && defaultState)
        }
      }

      override fun invoke(): Boolean = !value && defaultState
    }

    private class TokenGeneratorPredicate(private val generator: LoginTokenGenerator,
                                          private val serverTextField: ExtendableTextField)
      : ComponentPredicate() {
      override fun invoke(): Boolean = generator.canGenerateToken(serverTextField.text)

      override fun addListener(listener: (Boolean) -> Unit) =
        serverTextField.document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) = listener(invoke())
        })
    }

    private class CoroutineActivatable(private val block: suspend CoroutineScope.() -> Unit) : Activatable {

      private var scope: CoroutineScope? = null

      override fun showNotify() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).apply {
          launch { block() }
        }
      }

      override fun hideNotify() {
        scope?.cancel()
        scope = null
      }
    }
  }
}