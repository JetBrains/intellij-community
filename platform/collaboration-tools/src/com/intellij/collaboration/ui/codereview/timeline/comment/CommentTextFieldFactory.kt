// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

object CommentTextFieldFactory {
  fun create(
    model: CommentTextFieldModel,
    @Nls placeHolder: String,
    withValidation: Boolean = true
  ): EditorTextField = CommentTextField(model.project, model.document).apply {
    putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    setPlaceholder(placeHolder)
    addSettingsProvider {
      it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
      it.colorsScheme.lineSpacing = 1f
      it.settings.isUseSoftWraps = true
    }
    selectAll()
    if (withValidation) {
      UiNotifyConnector(this, ValidatorActivatable(model, this), false)
    }
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

  private class ValidatorActivatable(
    private val model: CommentTextFieldModel,
    private val textField: EditorTextField
  ) : Activatable {
    private var validatorDisposable: Disposable? = null
    private var validator: ComponentValidator? = null

    init {
      model.addStateListener {
        validator?.revalidate()
      }
    }

    override fun showNotify() {
      validatorDisposable = Disposer.newDisposable("ETF validator")
      validator = ComponentValidator(validatorDisposable!!).withValidator(Supplier {
        model.error?.let { ValidationInfo(it.message.orEmpty(), textField) }
      }).installOn(textField)
    }

    override fun hideNotify() {
      validatorDisposable?.let { Disposer.dispose(it) }
      validatorDisposable = null
      validator = null
    }
  }
}