// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter
import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.codeInsight.documentation.render.DocRenderPassFactory
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.hints.InlayHintsPassFactory.Companion.applyPlaceholders
import com.intellij.codeInsight.hints.InlayHintsPassFactory.Companion.collectPlaceholders
import com.intellij.codeInsight.hints.codeVision.CodeVisionPassFactory.Companion.applyPlaceholders
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.CompositeDataProvider
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Deferred
import java.util.concurrent.CancellationException

private val LOG = logger<PsiAwareTextEditorImpl>()

open class PsiAwareTextEditorImpl : TextEditorImpl {
  private var backgroundHighlighter: TextEditorBackgroundHighlighter? = null

  constructor(project: Project, file: VirtualFile, provider: TextEditorProvider) : super(project = project,
                                                                                         file = file,
                                                                                         provider = provider,
                                                                                         editor = createTextEditor(project, file))

  constructor(project: Project,
              file: VirtualFile,
              provider: TextEditorProvider,
              editor: EditorImpl) : super(project = project, file = file, provider = provider, editor = editor)

  internal constructor(project: Project,
                       file: VirtualFile,
                       asyncLoader: AsyncEditorLoader,
                       editor: EditorImpl) : super(project = project, file = file, editor = editor, asyncLoader = asyncLoader)

  override suspend fun loadEditorInBackground(highlighterDeferred: Deferred<EditorHighlighter>): Runnable {
    val highlighter = highlighterDeferred.await()
    val editor = editor
    return readAction {
      val psiFile = PsiManager.getInstance(project).findFile(file)
      val document = editor.document
      val foldingState = if (project.isDefault || !PsiDocumentManager.getInstance(project).isCommitted(document)) {
        null
      }
      else {
        catchingExceptions {
          CodeFoldingManager.getInstance(project).buildInitialFoldings(document)
        }
      }
      val focusZones = catchingExceptions { FocusModePassFactory.calcFocusZones(psiFile) }
      val items = if (psiFile != null && DocRenderManager.isDocRenderingEnabled(getEditor())) {
        catchingExceptions { DocRenderPassFactory.calculateItemsToRender(editor, psiFile) }
      }
      else {
        null
      }

      val buffer = if (psiFile == null) null else catchingExceptions { collectPlaceholders(file = psiFile, editor = editor) }
      val placeholders = catchingExceptions {
        CodeVisionInitializer.getInstance(project).getCodeVisionHost().collectPlaceholders(editor, psiFile)
      }

      Runnable {
        setupEditor(editor, highlighter)

        foldingState?.setToEditor(editor)
        if (focusZones != null) {
          FocusModePassFactory.setToEditor(focusZones, editor)
          if (editor is EditorImpl) {
            editor.applyFocusMode()
          }
        }
        if (items != null) {
          DocRenderPassFactory.applyItemsToRender(editor, project, items, true)
        }
        if (buffer != null) {
          applyPlaceholders(file = psiFile!!, editor = editor, hints = buffer)
        }
        if (!placeholders.isNullOrEmpty()) {
          applyPlaceholders(editor, placeholders)
        }
        if (psiFile != null && psiFile.isValid) {
          DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
        }
      }
    }
  }

  override fun createEditorComponent(project: Project, file: VirtualFile, editor: EditorImpl): TextEditorComponent {
    return PsiAwareTextEditorComponent(project = project, file = file, textEditor = this, editor = editor)
  }

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? {
    if (!isEditorLoaded(editor)) {
      return null
    }

    if (backgroundHighlighter == null) {
      backgroundHighlighter = TextEditorBackgroundHighlighter(project, editor)
    }
    return backgroundHighlighter
  }
}

private class PsiAwareTextEditorComponent(private val project: Project,
                                          file: VirtualFile,
                                          textEditor: TextEditorImpl,
                                          editor: EditorImpl) : TextEditorComponent(project = project,
                                                                                    file = file,
                                                                                    textEditor = textEditor,
                                                                                    editorImpl = editor) {
  override fun dispose() {
    super.dispose()
    project.serviceIfCreated<CodeFoldingManager>()?.releaseFoldings(editor)
  }

  override fun createBackgroundDataProvider(): DataProvider? {
    val superProvider = super.createBackgroundDataProvider() ?: return null
    return CompositeDataProvider.compose(
      { dataId ->
        if (PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.`is`(dataId)) {
          (LookupManager.getInstance(project).activeLookup as LookupImpl?)?.takeIf { it.isVisible }?.bounds
        }
        else {
          null
        }
      },
      superProvider,
    )
  }
}

private inline fun <T : Any> catchingExceptions(computable: () -> T?): T? {
  try {
    return computable()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    if (e is ControlFlowException) {
      throw e
    }
    LOG.warn("Exception during editor loading", e)
  }
  return null
}