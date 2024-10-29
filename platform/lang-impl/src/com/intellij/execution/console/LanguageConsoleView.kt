// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

interface LanguageConsoleView : ConsoleView, Disposable {
  companion object {
    @JvmField
    val EXECUTION_EDITOR_KEY: Key<ConsoleExecutionEditor> = Key("EXECUTION_EDITOR_KEY")
  }

  val project: Project

  var title: @NlsContexts.TabTitle String

  val file: PsiFile?

  val virtualFile: VirtualFile

  val currentEditor: EditorEx

  val consoleEditor: EditorEx

  val editorDocument: Document

  val historyViewer: EditorEx

  var language: Language

  var prompt: String?

  var promptAttributes: ConsoleViewContentType

  fun setInputText(inputText: String)

  var isEditable: Boolean

  var isConsoleEditorEnabled: Boolean
}
