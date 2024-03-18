// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.CompositeDataProvider
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.CancellationException

private val LOG = logger<PsiAwareTextEditorImpl>()

open class PsiAwareTextEditorImpl : TextEditorImpl {
  private var backgroundHighlighter: TextEditorBackgroundHighlighter? = null

  constructor(project: Project, file: VirtualFile, provider: TextEditorProvider) : super(project = project,
                                                                                         file = file,
                                                                                         provider = provider,
                                                                                         editor = createTextEditor(project, file))

  protected constructor(project: Project,
                        file: VirtualFile,
                        provider: TextEditorProvider,
                        editor: EditorImpl) : super(project = project, file = file, provider = provider, editor = editor)

  internal constructor(project: Project,
                       file: VirtualFile,
                       asyncLoader: AsyncEditorLoader,
                       editor: EditorImpl) : super(project = project, file = file, editor = editor, asyncLoader = asyncLoader)

  override fun createEditorComponent(project: Project, file: VirtualFile, editor: EditorImpl): TextEditorComponent {
    val component = PsiAwareTextEditorComponent(project = project, file = file, textEditor = this, editor = editor)

    component.addComponentListener(object: ComponentAdapter() {
      override fun componentShown(e: ComponentEvent?) {
        editor.component.isVisible = true
      }
      override fun componentHidden(e: ComponentEvent?) {
        editor.component.isVisible = false
      }
    })

    return component
  }

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? {
    if (!isEditorLoaded(editor)) {
      return null
    }

    var backgroundHighlighter = backgroundHighlighter
    if (backgroundHighlighter == null) {
      backgroundHighlighter = TextEditorBackgroundHighlighter(project, editor)
      this.backgroundHighlighter = backgroundHighlighter
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

// not `inline` to ensure that this function is not used for a `suspend` task
internal fun <T : Any> catchingExceptions(computable: () -> T?): T? {
  try {
    return computable()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: ProcessCanceledException) {
    // will throw if actually canceled
    ProgressManager.checkCanceled()
    // otherwise, this PCE is manual -> treat it like any other exception
    LOG.warn("Exception during editor loading", RuntimeException(e))
  }
  catch (e: Throwable) {
    LOG.warn("Exception during editor loading", if (e is ControlFlowException) RuntimeException(e) else e)
  }
  return null
}