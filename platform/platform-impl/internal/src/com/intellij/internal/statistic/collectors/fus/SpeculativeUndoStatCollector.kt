// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

/**
 * Should be in RD-only module, but FUS automation does not work there
 */
@ApiStatus.Internal
@IntellijInternalApi
object SpeculativeUndoStatCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("undo.speculative", 1)

  private val UNDO_CALL_COUNT_BEFORE_CORRUPTION = EventFields.Int("undo_call_count_before_corruption")

  private val UNDO_CORRUPTION = GROUP.registerVarargEvent(
    "undo.corruption",
    EventFields.FileType,
    UNDO_CALL_COUNT_BEFORE_CORRUPTION,
  )

  /**
   * Collects project id, file type and number of successful undo/redo requests before the corruption
   */
  fun logUndoCorruption(project: Project, fileEditor: FileEditor?, undoCount: Int) {
    UNDO_CORRUPTION.log(project) {
      val fileType = fileEditor?.file?.fileType
      add(EventFields.FileType with fileType)
      add(UNDO_CALL_COUNT_BEFORE_CORRUPTION with undoCount)
    }
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}