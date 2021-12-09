// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FileEditorCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("file.editor", 3)
    private val FILE_EDITOR_FIELD = EventFields.Class("fileEditor")
    private val ALTERNATIVE_FILE_EDITOR_SELECTED = GROUP.registerVarargEvent("alternative.file.editor.selected",
                                                                             FILE_EDITOR_FIELD,
                                                                             EventFields.AnonymizedPath,
                                                                             EventFields.PluginInfo)

    @JvmStatic
    fun logAlternativeFileEditorSelected(project: Project, file: VirtualFile, editor: FileEditor) {
      ALTERNATIVE_FILE_EDITOR_SELECTED.log(project, FILE_EDITOR_FIELD.with(editor.javaClass), EventFields.AnonymizedPath.with(file.path))
    }
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

}