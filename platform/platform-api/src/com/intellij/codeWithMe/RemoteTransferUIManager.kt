// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import javax.swing.JComponent

object RemoteTransferUIManager {
  private val editorForbidKey = Key.create<Boolean>("lux.localComponents.forbidEditor")
  private val wellBeControlizableKey = Key.create<Boolean>("lux.localComponents.wellBeControlizable")

  @JvmStatic
  fun forbidEditorBeControlization(editor: Editor) {
    editor.putUserData(editorForbidKey, true)
  }

  @JvmStatic
  fun setWellBeControlizable(component: JComponent) {
    component.putUserData(wellBeControlizableKey, true)
  }

  @JvmStatic
  fun isEditorBeControlizationForbidden(editor: Editor): Boolean {
    return editor.getUserData(editorForbidKey) == true
  }

  @JvmStatic
  fun isWellBeControlizable(component: JComponent): Boolean {
    return component.getUserData(wellBeControlizableKey) == true
  }
}