// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.statusBar

import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorInfoImpl
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.ide.lightEdit.LightEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.status.PositionPanel

class LightEditPositionWidget(project: Project, private val myEditorManager: LightEditorManager) : PositionPanel(
  project), LightEditorListener {
  protected var editor: Editor? = null
    private set

  fun install(statusBar: StatusBar) {
    super.install(statusBar)
    myEditorManager.addListener(this)
  }

  fun isOurEditor(editor: Editor?): Boolean {
    return editor != null && this.editor === editor && editor.component.isShowing
  }

  override fun afterSelect(editorInfo: LightEditorInfo?) {
    editor = LightEditorInfoImpl.getEditor(editorInfo)
  }
}