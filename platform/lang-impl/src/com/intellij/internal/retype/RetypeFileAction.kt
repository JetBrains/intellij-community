// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key

/**
 * @author yole
 */
class RetypeFileAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl ?: return
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val existingSession = editor.getUserData(RETYPE_SESSION_KEY)
    if (existingSession != null) {
      existingSession.stop()
    }
    else {
      val retypeOptionsDialog = RetypeOptionsDialog(project)
      if (!retypeOptionsDialog.showAndGet()) return
      val session = RetypeSession(project, editor, retypeOptionsDialog.retypeDelay, retypeOptionsDialog.threadDumpDelay)
      editor.putUserData(RETYPE_SESSION_KEY, session)
      session.start()
    }
  }

  override fun update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    e.presentation.isEnabled = e.project != null && editor != null
    val retypeSession = editor?.getUserData(RETYPE_SESSION_KEY)
    if (retypeSession != null) {
      e.presentation.text = "Stop Retyping"
    }
    else {
      e.presentation.text = "Retype Current File"
    }
  }
}

interface RetypeFileAssistant {
  fun acceptLookupElement(element: LookupElement): Boolean

  companion object {
    val EP_NAME = ExtensionPointName.create<RetypeFileAssistant>("com.intellij.retypeFileAssistant")
  }
}

val RETYPE_SESSION_KEY = Key.create<RetypeSession>("com.intellij.internal.retype.RetypeSession")
