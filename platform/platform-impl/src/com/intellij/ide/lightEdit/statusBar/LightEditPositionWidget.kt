// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.statusBar

import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorInfoImpl
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.ide.lightEdit.LightEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.openapi.wm.impl.status.EditorBasedWidgetHelper
import com.intellij.openapi.wm.impl.status.PositionPanel
import kotlinx.coroutines.CoroutineScope

internal class LightEditPositionWidget(
  scope: CoroutineScope,
  dataContext: WidgetPresentationDataContext,
  editorManager: LightEditorManager,
) : PositionPanel(dataContext = dataContext, scope = scope, helper = MyEditorBasedWidgetHelper(dataContext.project)) {
  init {
    editorManager.addListener(object : LightEditorListener {
      override fun afterSelect(editorInfo: LightEditorInfo?) {
        (helper as MyEditorBasedWidgetHelper).editor = LightEditorInfoImpl.getEditor(editorInfo)
      }
    })
  }
}

private class MyEditorBasedWidgetHelper(project: Project) : EditorBasedWidgetHelper(project) {
  var editor: Editor? = null

  override fun isOurEditor(editor: Editor?, statusBar: StatusBar?): Boolean {
    return editor != null && this.editor === editor && editor.component.isShowing
  }
}