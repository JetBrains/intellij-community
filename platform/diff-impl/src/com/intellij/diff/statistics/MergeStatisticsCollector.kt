// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.statistics

import com.intellij.diff.merge.MergeStatisticsAggregator
import com.intellij.diff.merge.MergeThreesideViewer.MergeResultSource
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object MergeStatisticsCollector : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("vcs.merge", 1)

  private val SOURCE: EnumEventField<MergeResultSource> = EventFields.Enum("source", MergeResultSource::class.java)
  private val CHANGES: IntEventField = EventFields.Int("changes")
  private val AUTO_RESOLVABLE = EventFields.Int("autoResolvable")
  private val CONFLICTS = EventFields.Int("conflicts")
  private val UNRESOLVED = EventFields.Int("unresolved")
  private val AI_RESOLVED = EventFields.Int("aiResolved")
  private val AI_ROLLED_BACK = EventFields.Int("rolledBackAfterAi")
  private val AI_UNDONE = EventFields.Int("undoneAfterAi")
  private val AI_EDITED = EventFields.Int("editedAfterAi")

  private val FILE_MERGED_EVENT: VarargEventId = GROUP.registerVarargEvent("file.merged", CHANGES, EventFields.DurationMs, AUTO_RESOLVABLE, CONFLICTS, UNRESOLVED, AI_RESOLVED, AI_ROLLED_BACK, AI_UNDONE)

  override fun getGroup(): EventLogGroup = GROUP

  fun logMergeFinished(project: Project?, source: MergeResultSource, aggregator: MergeStatisticsAggregator) {
    FILE_MERGED_EVENT.log(project) {
      add(SOURCE.with(source))
      add(CHANGES.with(aggregator.changes))
      add(EventFields.DurationMs.with(System.currentTimeMillis() - aggregator.initialTimestamp))
      add(AUTO_RESOLVABLE.with(aggregator.autoResolvable))
      add(CONFLICTS.with(aggregator.conflicts))
      add(UNRESOLVED.with(aggregator.unresolved))
      add(AI_RESOLVED.with(aggregator.resolvedByAi()))
      add(AI_ROLLED_BACK.with(aggregator.rolledBackAfterAI()))
      add(AI_UNDONE.with(aggregator.undoneAfterAI()))
      add(AI_EDITED.with(aggregator.editedAfterAI()))
    }
  }
}
