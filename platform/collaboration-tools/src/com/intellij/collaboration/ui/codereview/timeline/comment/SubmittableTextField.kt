// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls

class SubmittableTextField private constructor(
  val submittableModel: SubmittableTextFieldModel
) : EditorTextField(submittableModel.document, submittableModel.project, FileTypes.PLAIN_TEXT) {

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

  companion object {
    fun create(
      model: SubmittableTextFieldModel,
      @Nls placeHolder: String
    ): SubmittableTextField = SubmittableTextField(model).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      isOneLineMode = false
      setPlaceholder(placeHolder)
      addSettingsProvider {
        it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
        it.colorsScheme.lineSpacing = 1f
        it.settings.isUseSoftWraps = true
      }
      selectAll()
    }
  }
}