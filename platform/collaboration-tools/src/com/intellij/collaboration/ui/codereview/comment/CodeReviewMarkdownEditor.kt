// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting.ESSENTIAL
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil
import com.intellij.collaboration.ui.CodeReviewUiUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.EditorTextField
import com.intellij.util.LocalTimeCounter

internal object CodeReviewMarkdownEditor {
  private val INJECTION_PROCESSED = Key.create<Boolean>("CODEREVIEW_INJECTION_PROCESSED")

  fun create(project: Project, inline: Boolean = false, oneLine: Boolean = false): Editor {
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    val psiDocumentManager = PsiDocumentManager.getInstance(project)

    // setup markdown only if plugin is enabled
    val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("md").takeIf { it != FileTypes.UNKNOWN } ?: FileTypes.PLAIN_TEXT
    val psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("Dummy.md", fileType, "", LocalTimeCounter.currentTime(), true, false)

    val editorFactory = EditorFactory.getInstance()
    val document = runReadActionBlocking {
      psiDocumentManager.getDocument(psiFile)
    } ?: editorFactory.createDocument("")

    document.addDocumentListener(object : BulkAwareDocumentListener.Simple {
      override fun documentChangedNonBulk(event: DocumentEvent) {
        injectedLanguageManager.getCachedInjectedDocumentsInRange(psiFile, TextRange(event.offset, event.offset + event.newLength))
          .forEach { doc ->
            if (doc.getUserData(INJECTION_PROCESSED) == true) return@forEach
            val injectedPsi = psiDocumentManager.getCachedPsiFile(doc) ?: return@forEach
            HighlightLevelUtil.forceRootHighlighting(injectedPsi, ESSENTIAL)
            doc.putUserData(INJECTION_PROCESSED, true)
          }
      }
    })

    return (editorFactory.createEditor(document, project, fileType, false) as EditorEx).also {
      EditorTextField.setupTextFieldEditor(it)
    }.apply {
      settings.isCaretRowShown = false
      settings.isUseSoftWraps = true

      putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR, true)
      putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
      colorsScheme.lineSpacing = 1f
      isEmbeddedIntoDialogWrapper = true
      isOneLineMode = oneLine

      component.addPropertyChangeListener("font") {
        setEditorFontFromComponent()
      }
      setEditorFontFromComponent()

      setBorder(null)
      if (!inline) {
        CodeReviewUiUtil.setupStandaloneEditorOutlineBorder(this)
      }
      else if (!oneLine) {
        setVerticalScrollbarVisible(true)
      }
      contentComponent.setFocusCycleRoot(false)
    }
  }

  private fun EditorEx.setEditorFontFromComponent() {
    val font = component.font
    colorsScheme.editorFontName = font.name
    colorsScheme.editorFontSize = font.size
  }
}