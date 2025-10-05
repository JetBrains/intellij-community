// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.time.Duration

internal object IdentifierHighlightingFUSReporter {
  fun report(project: Project, file: VirtualFile?, offset: Int, result: IdentifierHighlightingResult, fromCache:Boolean, computationDurationMs: Long) {
    IdentifierHighlightingFUSCollector.HIGHLIGHTED.log(project,
       IdentifierHighlightingFUSCollector.DURATION_MS with computationDurationMs,
       IdentifierHighlightingFUSCollector.OFFSET with offset,
       IdentifierHighlightingFUSCollector.FROM_CACHE with fromCache,
       IdentifierHighlightingFUSCollector.OCCURRENCES with result.occurrences.size,
       IdentifierHighlightingFUSCollector.TARGETS with result.targets.size,
       EventFields.FileType with file?.fileType
    )
  }
}
internal object IdentifierHighlightingFUSCollector : CounterUsagesCollector() {
  @JvmField
  val GROUP: EventLogGroup = EventLogGroup("identifier.highlighting", 2)
  @JvmField
  val OFFSET: IntEventField = EventFields.Int("offset")
  @JvmField
  val FROM_CACHE: BooleanEventField = EventFields.Boolean("from_cache")
  /**
   * ident highlighter computing duration in ms
   */
  @JvmField
  val DURATION_MS: LongEventField = LongEventField("duration_ms")
  @JvmField
  val OCCURRENCES: IntEventField = EventFields.Int("occurrences")
  @JvmField
  val TARGETS: IntEventField = EventFields.Int("targets")

  @JvmField
  val HIGHLIGHTED: VarargEventId = GROUP.registerVarargEvent(
    "highlighted",
    DURATION_MS,
    OFFSET,
    FROM_CACHE,
    OCCURRENCES,
    TARGETS,
    EventFields.FileType,
  )

  override fun getGroup(): EventLogGroup = GROUP
}