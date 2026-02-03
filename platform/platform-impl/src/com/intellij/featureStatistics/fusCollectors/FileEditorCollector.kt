// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object FileEditorCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("file.editor", 6)
  private val FILE_EDITOR_FIELD = EventFields.Class("fileEditor")
  private val ALTERNATIVE_FILE_EDITOR_SELECTED = GROUP.registerVarargEvent("alternative.file.editor.selected",
                                                                           FILE_EDITOR_FIELD,
                                                                           EventFields.AnonymizedPath,
                                                                           EventFields.PluginInfo)
  private val EDITOR_EMPTY_STATE_SHOWN = GROUP.registerEvent("file.editor.empty.state.shown",
                                                             EventFields.Enum<EmptyStateCause>("empty_state_cause"))
  private val EDITOR_MARKUP_RESTORED = GROUP.registerEvent("file.editor.markup.restored",
                                                           EventFields.AnonymizedPath,
                                                           EventFields.Enum<MarkupGraveEvent>("markup_grave_event"),
                                                           EventFields.Int("restored_highlighters"))

  fun logAlternativeFileEditorSelected(project: Project, file: VirtualFile, editor: FileEditor) {
    ALTERNATIVE_FILE_EDITOR_SELECTED.log(
      project = project,
      pairs = listOf(FILE_EDITOR_FIELD.with(editor.javaClass), EventFields.AnonymizedPath.with(file.path)),
    )
  }

  fun logEditorEmptyState(project: Project, cause: EmptyStateCause) {
    EDITOR_EMPTY_STATE_SHOWN.log(project, cause)
  }

  fun logEditorMarkupGrave(project: Project, file: VirtualFile, graveEvent: MarkupGraveEvent, restoredCount: Int) {
    EDITOR_MARKUP_RESTORED.log(project, file.path, graveEvent, restoredCount)
  }

  override fun getGroup(): EventLogGroup = GROUP

  enum class EmptyStateCause {
    ALL_TABS_CLOSED,
    PROJECT_OPENED,
    CONTEXT_RESTORED,
  }

  enum class MarkupGraveEvent {
    RESTORED,
    NOT_RESTORED_CACHE_MISS,
    NOT_RESTORED_CONTENT_CHANGED,
  }
}
