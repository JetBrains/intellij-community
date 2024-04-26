// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.highlighter

import com.intellij.lang.LanguageUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.SlowOperations

class EditorHighlighterFactoryImpl : EditorHighlighterFactory() {
  override fun createEditorHighlighter(highlighter: SyntaxHighlighter?, colors: EditorColorsScheme): EditorHighlighter {
    return LexerEditorHighlighter(highlighter ?: PlainSyntaxHighlighter(), colors)
  }

  override fun createEditorHighlighter(fileType: FileType, settings: EditorColorsScheme, project: Project?): EditorHighlighter {
    if (fileType is LanguageFileType) {
      return FileTypeEditorHighlighterProviders.INSTANCE.forFileType(fileType).getEditorHighlighter(project, fileType, null, settings)
    }

    val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, null)
    return createEditorHighlighter(highlighter, settings)
  }

  override fun createEditorHighlighter(project: Project?, fileType: FileType): EditorHighlighter {
    return createEditorHighlighter(fileType = fileType, settings = EditorColorsManager.getInstance().globalScheme, project = project)
  }

  override fun createEditorHighlighter(file: VirtualFile, editorColorScheme: EditorColorsScheme, project: Project?): EditorHighlighter {
    val fileType = file.fileType
    if (fileType is LanguageFileType) {
      val substLang = if (project == null) null else LanguageUtil.getLanguageForPsi(project, file, fileType)
      val substFileType = if (substLang != null && substLang !== fileType.language) substLang.associatedFileType else null
      if (substFileType != null) {
        val provider = FileTypeEditorHighlighterProviders.INSTANCE.forFileType(substFileType)
        val editorHighlighter = provider.getEditorHighlighter(project, substFileType, file, editorColorScheme)
        val isPlain = editorHighlighter.javaClass == LexerEditorHighlighter::class.java &&
                      (editorHighlighter as LexerEditorHighlighter).isPlain
        if (!isPlain) {
          return editorHighlighter
        }
      }

      try {
        SlowOperations.knownIssue("IDEA-333907, EA-821093").use {
          return FileTypeEditorHighlighterProviders.INSTANCE.forFileType(fileType)
            .getEditorHighlighter(project, fileType, file, editorColorScheme)
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        thisLogger().error(e)
      }
    }

    val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, file)
    return createEditorHighlighter(highlighter = highlighter, colors = editorColorScheme)
  }

  override fun createEditorHighlighter(project: Project?, file: VirtualFile): EditorHighlighter {
    return createEditorHighlighter(file = file, editorColorScheme = EditorColorsManager.getInstance().globalScheme, project = project)
  }

  override fun createEditorHighlighter(project: Project?, fileName: String): EditorHighlighter {
    return createEditorHighlighter(settings = EditorColorsManager.getInstance().globalScheme, fileName = fileName, project = project)
  }

  override fun createEditorHighlighter(settings: EditorColorsScheme, fileName: String, project: Project?): EditorHighlighter {
    return createEditorHighlighter(file = LightVirtualFile(fileName), editorColorScheme = settings, project = project)
  }
}
