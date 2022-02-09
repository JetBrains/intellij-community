// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil
import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager

class HighlightingReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean, fileIsOpenAlready: Boolean) {
    if (!fileIsOpenAlready) return

    val highlighting =
      if (readerMode && !ReaderModeSettings.getInstance(project).showWarnings) FileHighlightingSetting.SKIP_INSPECTION
      else FileHighlightingSetting.FORCE_HIGHLIGHTING

    HighlightLevelUtil.forceRootHighlighting(PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return, highlighting)
  }
}

class ReaderModeHighlightingSettingsProvider : DefaultHighlightingSettingProvider() {
  override fun getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting? {
    if (ReaderModeSettings.getInstance(project).enabled
        && !ReaderModeSettings.getInstance(project).showWarnings
        && ReaderModeSettings.matchMode(project, file)) {
      return FileHighlightingSetting.SKIP_INSPECTION
    }

    return null
  }
}

class LigaturesReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean, fileIsOpenAlready: Boolean) {
    val scheme = editor.colorsScheme
    val preferences = scheme.fontPreferences
    scheme.fontPreferences =
      FontPreferencesImpl().also {
        preferences.copyTo(it)
        it.setUseLigatures(if (readerMode) {
          ReaderModeSettings.getInstance(project).showLigatures
        } else {
          (AppEditorFontOptions.getInstance().fontPreferences as FontPreferencesImpl).useLigatures()
        })
      }
  }
}

class FontReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean, fileIsOpenAlready: Boolean) {
    val lineSpacing = AppEditorFontOptions.getInstance().fontPreferences.lineSpacing
    setLineSpacing(editor, if (readerMode && ReaderModeSettings.getInstance(project).increaseLineSpacing) { lineSpacing * 1.2f } else lineSpacing)
  }

  private fun setLineSpacing(editor: Editor, lineSpacing: Float) {
    EditorScrollingPositionKeeper.perform(editor, false) {
      editor.colorsScheme.lineSpacing = lineSpacing
    }
  }
}

class DocsRenderingReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean, fileIsOpenAlready: Boolean) {
    DocRenderManager.setDocRenderingEnabled(editor, if (readerMode) {
      ReaderModeSettings.getInstance(project).showRenderedDocs
    } else {
      EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled
    })
  }
}