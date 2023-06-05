// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.SingleAlarm

internal class StatusBarUpdater(private val project: Project, parentDisposable: Disposable) {
  private val alarm: SingleAlarm

  init {
    alarm = SingleAlarm({ updateStatus() }, 100, parentDisposable)
    project.messageBus.connect(parentDisposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                                                           object : FileEditorManagerListener {
                                                               override fun selectionChanged(event: FileEditorManagerEvent) {
                                                                 updateLater()
                                                               }
                                                             })
    project.messageBus.connect(parentDisposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonListener {
      override fun daemonFinished() {
        updateLater()
      }
    })
  }

  private fun updateLater() {
    alarm.cancelAndRequest()
  }

  private fun updateStatus() {
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
    val text = if (info != null && info.description != null) info.description else ""
    val statusBar = WindowManager.getInstance().getStatusBar(editor.contentComponent, project)
    if (statusBar != null && text != statusBar.info) {
      statusBar.setInfo(text, "updater")
    }
  }

  companion object {
    private val MIN = HighlightSeverity("min", HighlightSeverity.INFORMATION.myVal + 1)
  }
}
