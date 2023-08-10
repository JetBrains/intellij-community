// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object FileEditorCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("file.editor", 4)
  private val FILE_EDITOR_FIELD = EventFields.Class("fileEditor")
  private val ALTERNATIVE_FILE_EDITOR_SELECTED = GROUP.registerVarargEvent("alternative.file.editor.selected",
                                                                           FILE_EDITOR_FIELD,
                                                                           EventFields.AnonymizedPath,
                                                                           EventFields.PluginInfo)
  private val EDITOR_EMPTY_STATE_SHOWN = GROUP.registerEvent("file.editor.empty.state.shown",
                                                             EventFields.Enum<EmptyStateCause>("empty_state_cause"))

  @JvmStatic
  fun logAlternativeFileEditorSelected(project: Project, file: VirtualFile, editor: FileEditor) {
    ALTERNATIVE_FILE_EDITOR_SELECTED.log(project, FILE_EDITOR_FIELD.with(editor.javaClass), EventFields.AnonymizedPath.with(file.path))
  }

  @JvmStatic
  fun logEditorEmptyState(project: Project, cause: EmptyStateCause) {
    EDITOR_EMPTY_STATE_SHOWN.log(project, cause)
  }

  override fun getGroup(): EventLogGroup = GROUP

  enum class EmptyStateCause {
    ALL_TABS_CLOSED,
    PROJECT_OPENED,
    CONTEXT_RESTORED,
  }
}
