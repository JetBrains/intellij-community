// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

object CommentTextFieldFactory {
  fun create(
    project: Project?,
    document: Document,
    scrollOnChange: ScrollOnChangePolicy = ScrollOnChangePolicy.ScrollToField,
    placeHolder: @Nls String? = null
  ): EditorTextField = CommentTextField(project, document).apply {
    putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    setPlaceholder(placeHolder)
    addSettingsProvider {
      it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
      it.colorsScheme.lineSpacing = 1f
      it.settings.isUseSoftWraps = true
      it.isEmbeddedIntoDialogWrapper = true
      it.contentComponent.isOpaque = false
    }
    installScrollIfChangedController(scrollOnChange)
    selectAll()
  }

  private fun EditorTextField.installScrollIfChangedController(policy: ScrollOnChangePolicy) {
    if (policy == ScrollOnChangePolicy.DontScroll) return

    fun scroll() {
      val field = this
      val parent = field.parent as? JComponent
      when (policy) {
        is ScrollOnChangePolicy.ScrollToComponent -> {
          val componentToScroll = policy.component
          parent?.scrollRectToVisible(Rectangle(0, 0, componentToScroll.width, componentToScroll.height))
        }
        ScrollOnChangePolicy.ScrollToField -> {
          parent?.scrollRectToVisible(Rectangle(0, 0, parent.width, parent.height))
        }
        else -> Unit
      }
    }

    addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        scroll()
      }
    })

    // Previous listener doesn't work properly when text field's size is changed because component is not resized at this moment.
    // Without the following listener component will not be scrolled to when newline is inserted.
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        if (UIUtil.isFocusAncestor(parent)) {
          scroll()
        }
      }
    })
  }

  sealed class ScrollOnChangePolicy {
    object DontScroll : ScrollOnChangePolicy()
    object ScrollToField : ScrollOnChangePolicy()
    class ScrollToComponent(val component: JComponent) : ScrollOnChangePolicy()
  }

  private class CommentTextField(
    project: Project?,
    document: Document
  ) : EditorTextField(document, project, FileTypes.PLAIN_TEXT) {
    init {
      isOneLineMode = false
    }

    //always paint pretty border
    override fun updateBorder(editor: EditorEx) = setupBorder(editor)

    override fun createEditor(): EditorEx {
      // otherwise border background is painted from multiple places
      return super.createEditor().apply {
        //TODO: fix in editor
        //com.intellij.openapi.editor.impl.EditorImpl.getComponent() == non-opaque JPanel
        // which uses default panel color
        component.isOpaque = false
        //com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder.paintBorder
        scrollPane.isOpaque = false
      }
    }

    override fun getData(dataId: String): Any? {
      if (PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId)) {
        return editor?.let { TextEditorProvider.getInstance().getTextEditor(it) } ?: super.getData(dataId)
      }
      return super.getData(dataId)
    }
  }
}