// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.getMarkdownLanguage
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.collaboration.util.exceptionOrNull
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.ui.LanguageTextField
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.JComponent

object CodeReviewCommentTextFieldFactory {
  fun createIn(cs: CoroutineScope, vm: CodeReviewSubmittableTextViewModel,
               actions: CommentInputActionsComponentFactory.Config,
               icon: CommentTextFieldFactory.IconConfig? = null): JComponent {
    val document = LanguageTextField.createDocument(
      "",
      getMarkdownLanguage() ?: PlainTextLanguage.INSTANCE,
      vm.project,
      LanguageTextField.SimpleDocumentCreator()
    )

    document.bindTextIn(cs, vm.text)

    val textField = CommentTextFieldFactory.create(vm.project, document)

    UiNotifyConnector.installOn(textField, object : Activatable {
      private var focusListenerJob: Job? = null

      override fun showNotify() {
        focusListenerJob = cs.launchNow {
          vm.focusRequests.collect {
            CollaborationToolsUIUtil.focusPanel(textField)
          }
        }
      }

      override fun hideNotify() {
        focusListenerJob?.cancel()
      }
    })

    val busyValue = vm.state.mapToValueModel(cs) {
      it?.isInProgress ?: false
    }
    val errorValue = vm.state.mapToValueModel(cs) {
      it?.exceptionOrNull()?.localizedMessage
    }

    CollaborationToolsUIUtil.installValidator(textField, errorValue)
    val inputField = CollaborationToolsUIUtil.wrapWithProgressOverlay(textField, busyValue).let {
      if (icon != null) {
        CommentTextFieldFactory.wrapWithLeftIcon(icon, it)
      }
      else {
        it
      }
    }

    return CommentInputActionsComponentFactory.attachActions(cs, inputField, actions)
  }

  private fun <T, R> StateFlow<T>.mapToValueModel(cs: CoroutineScope, mapper: (T) -> R): SingleValueModel<R> =
    SingleValueModel(mapper(value)).apply {
      cs.launch {
        collect {
          value = mapper(it)
        }
      }
    }
}

fun <VM : CodeReviewSubmittableTextViewModel> VM.submitActionIn(cs: CoroutineScope, name: @Nls String, doSubmit: VM.() -> Unit): Action {
  return swingAction(name) {
    doSubmit()
  }.apply {
    isEnabled = false
    bindEnabledIn(cs, text.combine(state) { text, state -> text.isNotBlank() && state?.isInProgress != true })
  }
}