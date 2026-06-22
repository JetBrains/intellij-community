// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.collaboration.util.exceptionOrNull
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.ui.JBColor
import com.intellij.util.asSafely
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.JComponent

object CodeReviewCommentTextFieldFactory {
  fun createIn(
    cs: CoroutineScope, vm: CodeReviewSubmittableTextViewModel,
    actions: CommentInputActionsComponentFactory.Config,
    icon: CommentTextFieldFactory.IconConfig? = null,
  ): JComponent = createIn(cs, vm, actions, icon) {}

  fun createIn(
    cs: CoroutineScope, vm: CodeReviewSubmittableTextViewModel,
    actions: CommentInputActionsComponentFactory.Config,
    icon: CommentTextFieldFactory.IconConfig? = null,
    setupEditor: (Editor) -> Unit = {},
  ): JComponent {
    val editor = CodeReviewMarkdownEditor.create(vm.project).apply {
      component.isOpaque = false
      val fieldBackground = JBColor.lazy {
        if (component.isEnabled) UIUtil.getTextFieldBackground() else UIUtil.getTextFieldDisabledBackground()
      }
      component.background = fieldBackground
      asSafely<EditorEx>()?.backgroundColor = fieldBackground
    }
    cs.launchNow {
      try {
        editor.document.bindTextIn(this, vm.text)
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          EditorFactory.getInstance().releaseEditor(editor)
        }
      }
    }
    cs.launch {
      editor.scrollToCursorPositionWhenTyping()
    }

    val textLength = editor.document.textLength
    if (textLength > 0) {
      editor.selectionModel.setSelection(0, textLength)
    }

    UiNotifyConnector.installOn(editor.component, object : Activatable {
      private var focusListenerJob: Job? = null

      override fun showNotify() {
        focusListenerJob = cs.launchNow {
          vm.focusRequests.collect {
            CollaborationToolsUIUtil.focusPanel(editor.component)
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
    busyValue.addAndInvokeListener {
      editor.contentComponent.isEnabled = !it
    }

    CollaborationToolsUIUtil.installValidator(editor.component, errorValue)

    setupEditor(editor)

    val editorComponent = UiDataProvider.wrapComponent(editor.component) { sink ->
      // required for undo/redo
      sink[PlatformCoreDataKeys.FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(editor)
    }

    val inputField = CollaborationToolsUIUtil.wrapWithProgressOverlay(editorComponent, busyValue).let {
      if (icon != null) {
        CommentTextFieldFactory.wrapWithLeftIcon(icon, it) {
          val outerBorderInsets = editor.component.border.getBorderInsets(editor.component)
          val innerBorderInsets = editor.asSafely<EditorEx>()?.let { editor ->
            editor.scrollPane.border.getBorderInsets(editor.scrollPane)
          } ?: JBInsets.emptyInsets()
          editor.lineHeight + outerBorderInsets.top + outerBorderInsets.bottom + innerBorderInsets.top + innerBorderInsets.bottom
        }
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