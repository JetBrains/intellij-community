// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

private val MIN = HighlightSeverity("min", HighlightSeverity.INFORMATION.myVal + 1)

@OptIn(FlowPreview::class)
internal class StatusBarUpdater(private val project: Project, coroutineScope: CoroutineScope) {
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      updateRequests
        .debounce(100.milliseconds)
        .collectLatest {
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            updateStatus(project)
          }
        }
    }

    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        updateLater()
      }
    })
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
      override fun daemonFinished() {
        updateLater()
      }
    })
  }

  private fun updateLater() {
    check(updateRequests.tryEmit(Unit))
  }
}

private fun updateStatus(project: Project) {
  val editor = FileEditorManager.getInstance(project).selectedTextEditor
  if (editor == null || !editor.contentComponent.hasFocus()) {
    return
  }

  val document = editor.document
  if (document.isInBulkUpdate) {
    return
  }

  val offset = editor.caretModel.offset
  val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
  val info = (codeAnalyzer as DaemonCodeAnalyzerImpl).findHighlightByOffset(document, offset, false, MIN)
  val text = if (info == null || info.description == null) "" else info.description
  val statusBar = WindowManager.getInstance().getStatusBar(editor.contentComponent, project)
  if (statusBar != null && text != statusBar.info) {
    statusBar.setInfo(text, "updater")
  }
}

