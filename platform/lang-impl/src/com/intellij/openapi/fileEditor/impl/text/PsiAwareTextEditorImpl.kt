// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
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

  // for backward-compatibility only
  constructor(project: Project, file: VirtualFile, provider: TextEditorProvider) : super(
    project = project,
    file = file,
    componentAndLoader = createPsiAwareTextEditorComponent(
      file = file,
      editorAndLoader = createEditorImpl(
        project = project,
        file = file,
        asyncLoader = createAsyncEditorLoader(provider = provider, project = project, fileForTelemetry = file, editorCoroutineScope = null),
      ),
    ),
  )

  internal constructor(project: Project, file: VirtualFile, component: TextEditorComponent, asyncLoader: AsyncEditorLoader) : super(
    project = project,
    file = file,
    component = component,
    asyncLoader = asyncLoader,
    startLoading = false,
  )

  protected constructor(project: Project, file: VirtualFile, provider: TextEditorProvider, editor: EditorImpl) : super(
    project = project,
    file = file,
    componentAndLoader = createPsiAwareTextEditorComponent(
      file = file,
      editorAndLoader = editor to createAsyncEditorLoader(
        provider = provider,
        project = project,
        fileForTelemetry = file,
        editorCoroutineScope = null,
      ),
    ),
  )

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? {
    if (!asyncLoader.isLoaded()) {
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

private fun createPsiAwareTextEditorComponent(
  file: VirtualFile,
  editorAndLoader: Pair<EditorImpl, AsyncEditorLoader>,
) = createPsiAwareTextEditorComponent(file = file, editor = editorAndLoader.first) to editorAndLoader.second

internal fun createPsiAwareTextEditorComponent(file: VirtualFile, editor: EditorImpl): TextEditorComponent {
  val component = PsiAwareTextEditorComponent(file = file, editor = editor)
  component.addComponentListener(object : ComponentAdapter() {
    override fun componentShown(e: ComponentEvent?) {
      editor.component.isVisible = true
    }

    override fun componentHidden(e: ComponentEvent?) {
      editor.component.isVisible = false
    }
  })
  return component
}

private class PsiAwareTextEditorComponent(file: VirtualFile, editor: EditorImpl) : TextEditorComponent(file = file, editorImpl = editor) {
  override fun dispose() {
    val project = editor.project
    super.dispose()
    project?.serviceIfCreated<CodeFoldingManager>()?.releaseFoldings(editor)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    val project = editor.project
    if (project != null && !project.isDisposed) {
      val lookup = LookupManager.getInstance(project).activeLookup as LookupImpl?
      sink[PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE] = lookup?.takeIf { it.isVisible }?.bounds
    }
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