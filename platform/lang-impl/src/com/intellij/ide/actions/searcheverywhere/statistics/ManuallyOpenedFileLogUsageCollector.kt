package com.intellij.ide.actions.searcheverywhere.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ManuallyOpenedFileLogUsageCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("file", 1)

    private val OPENED = GROUP.registerEvent("manually.opened")

    fun VirtualFile.logFileManuallyOpen(project: Project) {
      if (FileEditorManager.getInstance(project).isFileOpen(this)) return

      logFileOpen(project)
    }


    private fun logFileOpen(project: Project) {
      OPENED.log(project)
    }
  }


  override fun getGroup() = GROUP
}

