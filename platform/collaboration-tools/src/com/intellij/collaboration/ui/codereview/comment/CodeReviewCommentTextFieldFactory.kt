// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory.ScrollOnChangePolicy
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.collaboration.util.exceptionOrNull
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.JBColor
import com.intellij.util.asSafely
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.jetbrains.annotations.Nls
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
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
    // also forces component revalidation on newline
    // if this is removed, a separate document listener is required
    editor.installScrollIfChangedController(ScrollOnChangePolicy.ScrollToField)
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

    val inputField = CollaborationToolsUIUtil.wrapWithProgressOverlay(editor.component, busyValue).let {
      if (icon != null) {
        CommentTextFieldFactory.wrapWithLeftIcon(icon, it) {
          val borderInsets = editor.component.border.getBorderInsets(editor.component)
          editor.lineHeight + borderInsets.top + borderInsets.bottom
        }
      }
      else {
        it
      }
    }

    return CommentInputActionsComponentFactory.attachActions(cs, inputField, actions)
  }

  private fun Editor.installScrollIfChangedController(policy: ScrollOnChangePolicy) {
    if (policy == ScrollOnChangePolicy.DontScroll) return

    fun scroll() {
      val editor = this
      val parent = editor.component.parent as? JComponent ?: return
      when (policy) {
        is ScrollOnChangePolicy.ScrollToComponent -> {
          val componentToScroll = policy.component
          parent.scrollRectToVisible(Rectangle(0, 0, componentToScroll.width, componentToScroll.height))
        }
        ScrollOnChangePolicy.ScrollToField -> {
          parent.scrollRectToVisible(Rectangle(0, 0, parent.width, parent.height))
        }
        else -> Unit
      }
    }

    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        scroll()
      }
    })

    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        val parent = e?.component?.parent ?: return
        if (UIUtil.isFocusAncestor(parent)) {
          scroll()
        }
      }
    })
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