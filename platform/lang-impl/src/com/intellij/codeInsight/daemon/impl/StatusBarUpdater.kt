// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.codeInsight.multiverse.EditorContextManager.Companion.getInstance
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

private val MIN = HighlightSeverity("min", HighlightSeverity.INFORMATION.myVal + 1)

private class DaemonCodeAnalyzerStatusBarUpdater : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    coroutineScope {
      initStatusBarUpdater(project = project, coroutineScope = this)
    }
  }
}

@OptIn(FlowPreview::class)
private suspend fun initStatusBarUpdater(project: Project, coroutineScope: CoroutineScope) {
  val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val daemonCodeAnalyzer = project.serviceAsync<DaemonCodeAnalyzer>() as? DaemonCodeAnalyzerImpl ?: return

  val connection = project.messageBus.connect(coroutineScope)
  connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      check(updateRequests.tryEmit(Unit))
    }
  })
  connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
    override fun daemonFinished() {
      check(updateRequests.tryEmit(Unit))
    }
  })

  updateRequests
    .debounce(100.milliseconds)
    .collectLatest {
      updateStatus(project, daemonCodeAnalyzer)
    }
}

private suspend fun updateStatus(project: Project, daemonCodeAnalyzer: DaemonCodeAnalyzerImpl) {
  val fileEditorManager = project.serviceAsync<FileEditorManager>()
  val (editor, statusBar) = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    val editor = fileEditorManager.selectedTextEditor?.takeIf { it.contentComponent.hasFocus() } ?: return@withContext null
    val statusBar = serviceAsync<WindowManager>().getStatusBar(editor.contentComponent, project) ?: return@withContext null
    editor to statusBar
  } ?: return

  val text = readAction {
    val document = editor.document
    if (document.isInBulkUpdate) {
      return@readAction null
    }

    if (editor.isDisposed) {
      return@readAction null
    }

    val offset = editor.caretModel.offset
    val editorContextManager = getInstance(project)
    val context = editorContextManager.getEditorContexts(editor).mainContext
    daemonCodeAnalyzer.findHighlightByOffset(document, offset, false, MIN, context)?.description ?: ""
  } ?: return

  if (text.takeIf { it.isNotEmpty() } != statusBar.info?.takeIf { it.isNotEmpty() }) {
    withContext(Dispatchers.EDT) {
      statusBar.setInfo(text, "updater")
    }
  }
}