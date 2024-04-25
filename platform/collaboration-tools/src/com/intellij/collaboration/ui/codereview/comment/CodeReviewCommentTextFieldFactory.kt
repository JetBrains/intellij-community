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
import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.util.LocalTimeCounter
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.border.Border

object CodeReviewCommentTextFieldFactory {
  fun createIn(cs: CoroutineScope, vm: CodeReviewSubmittableTextViewModel,
               actions: CommentInputActionsComponentFactory.Config,
               icon: CommentTextFieldFactory.IconConfig? = null): JComponent {
    val editor = createMarkdownEditorFieldIn(cs, vm.project)
    editor.document.bindTextIn(cs, vm.text)
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

  private fun createMarkdownEditorFieldIn(
    cs: CoroutineScope,
    project: Project,
    scrollOnChange: ScrollOnChangePolicy = ScrollOnChangePolicy.ScrollToField
  ): Editor {
    val editor = createEditor(project)
    cs.launchNow {
      try {
        awaitCancellation()
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }

    // also forces component revalidation on newline
    // if this is removed, a separate document listener is required
    editor.installScrollIfChangedController(scrollOnChange)
    return editor
  }

  private fun createEditor(project: Project): EditorEx {
    // setup markdown only if plugin is enabled
    val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("md").takeIf { it != FileTypes.UNKNOWN } ?: FileTypes.PLAIN_TEXT
    val psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("Dummy.md", fileType, "", LocalTimeCounter.currentTime(), true, false)

    val editorFactory = EditorFactory.getInstance()
    val document = ReadAction.compute<Document, Throwable> {
      PsiDocumentManager.getInstance(project).getDocument(psiFile)
    } ?: editorFactory.createDocument("")

    return (editorFactory.createEditor(document, project, fileType, false) as EditorEx).also {
      EditorTextField.setupTextFieldEditor(it)
    }.apply {
      settings.isCaretRowShown = false
      settings.isUseSoftWraps = true

      putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR, true)
      putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
      colorsScheme.lineSpacing = 1f
      isEmbeddedIntoDialogWrapper = true

      component.addPropertyChangeListener("font") {
        setEditorFontFromComponent()
      }
      setEditorFontFromComponent()

      setBorder(null)
      component.border = EditorFocusBorder()
      component.isOpaque = false

      val fieldBackground = JBColor.lazy {
        if (component.isEnabled) UIUtil.getTextFieldBackground() else UIUtil.getTextFieldDisabledBackground()
      }
      component.background = fieldBackground
      // TODO: handle theme change
      backgroundColor = fieldBackground
    }
  }

  private fun EditorEx.setEditorFontFromComponent() {
    val font = component.font
    colorsScheme.editorFontName = font.name
    colorsScheme.editorFontSize = font.size
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

// can't use DarculaTextBorderNew because of nested focus and because it's a UIResource
private class EditorFocusBorder : Border, ErrorBorderCapable {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val hasFocus = UIUtil.isFocusAncestor(c)

    val rect = Rectangle(x, y, width, height).also {
      val maxBorderThickness = DarculaUIUtil.BW.get()
      JBInsets.removeFrom(it, JBInsets.create(maxBorderThickness, maxBorderThickness))
    }
    DarculaNewUIUtil.fillInsideComponentBorder(g, rect, c.background)
    DarculaNewUIUtil.paintComponentBorder(g, rect, DarculaUIUtil.getOutline(c as JComponent), hasFocus, c.isEnabled)
  }

  // the true vertical inset would be 7, but Editor has 1px padding above and below the line
  override fun getBorderInsets(c: Component): Insets = JBInsets.create(6, 10)
  override fun isBorderOpaque(): Boolean = false
}