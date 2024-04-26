// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.highlighter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class EditorHighlighterFactory {
  companion object {
    @JvmStatic
    fun getInstance(): EditorHighlighterFactory = ApplicationManager.getApplication().service<EditorHighlighterFactory>()
  }

  abstract fun createEditorHighlighter(highlighter: SyntaxHighlighter?, colors: EditorColorsScheme): EditorHighlighter

  abstract fun createEditorHighlighter(fileType: FileType, settings: EditorColorsScheme, project: Project?): EditorHighlighter

  abstract fun createEditorHighlighter(project: Project?, fileType: FileType): EditorHighlighter

  abstract fun createEditorHighlighter(file: VirtualFile, editorColorScheme: EditorColorsScheme, project: Project?): EditorHighlighter

  abstract fun createEditorHighlighter(project: Project?, file: VirtualFile): EditorHighlighter

  abstract fun createEditorHighlighter(project: Project?, fileName: String): EditorHighlighter

  abstract fun createEditorHighlighter(settings: EditorColorsScheme, fileName: String, project: Project?): EditorHighlighter
}