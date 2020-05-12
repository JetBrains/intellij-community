// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil
import com.intellij.codeInsight.documentation.render.DocRenderItem
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.xml.breadcrumbs.BreadcrumbsForceShownSettings
import com.intellij.xml.breadcrumbs.BreadcrumbsInitializingActivity

class BreadcrumbsReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean) {
    val showBreadcrumbs = (readerMode && ReaderModeSettings.instance(project).showBreadcrumbs)
                          || EditorSettingsExternalizable.getInstance().isBreadcrumbsShown
    BreadcrumbsForceShownSettings.setForcedShown(showBreadcrumbs, editor)
    ApplicationManager.getApplication().invokeLater { BreadcrumbsInitializingActivity.reinitBreadcrumbsInAllEditors(project) }
  }
}

class HighlightingReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean) {
    val highlighting =
      if (readerMode && ReaderModeSettings.instance(project).hideWarnings) FileHighlightingSetting.SKIP_INSPECTION
      else FileHighlightingSetting.FORCE_HIGHLIGHTING

    HighlightLevelUtil.forceRootHighlighting(PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return, highlighting)
  }
}

class LigaturesReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean) {
    val scheme = editor.colorsScheme
    val preferences = scheme.fontPreferences

    var useLigatures: Boolean = (AppEditorFontOptions.getInstance().fontPreferences as FontPreferencesImpl).useLigatures()
    if (readerMode) {
      if (ReaderModeSettings.instance(project).showLigatures) {
        useLigatures = true

        val ligaturesFontPreferences = FontPreferencesImpl()
        preferences.copyTo(ligaturesFontPreferences)
        ligaturesFontPreferences.setUseLigatures(useLigatures)
        scheme.fontPreferences = ligaturesFontPreferences
        (scheme.fontPreferences as FontPreferencesImpl).setUseLigatures(true)
      }
    }
  }
}


class FontReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean) {
    if (readerMode) {
      if (ReaderModeSettings.instance(project).increaseLineSpacing) {
        editor.colorsScheme.lineSpacing = 1.4f
      }
    }
    else {
      editor.colorsScheme.lineSpacing = AppEditorFontOptions.getInstance().fontPreferences.lineSpacing
    }
  }
}

class DocsRenderingReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean) {
    if (readerMode) {
      if (ReaderModeSettings.instance(project).showRenderedDocs) {
        EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled = true
      }
    }
    else {
      EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled = false
    }

    DocRenderItem.resetToDefaultEditorState(editor)
  }
}

class InlaysReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean) {
    if (readerMode) {
      if (ReaderModeSettings.instance(project).showInlaysHints) {
        InlayHintsPassFactory.setHintsEnabled(editor, true)
      }
    }
    else {
      InlayHintsPassFactory.setHintsEnabled(editor, false)
    }
  }
}