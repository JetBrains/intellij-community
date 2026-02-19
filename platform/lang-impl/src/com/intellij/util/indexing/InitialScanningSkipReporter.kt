// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
internal object InitialScanningSkipReporter {

  val GROUP = EventLogGroup("indexing.initial.scanning.skip", 2)

  internal enum class SourceOfScanning { OnProjectOpen, IndexTumblerOn }

  private val sourceOfScanningField = EventFields.Enum("source", SourceOfScanning::class.java)
  private val projectDirtyFilesCountField = EventFields.Int("project_dirty_files_count",
                                                            "Number of dirty files from project queue (files that were changed before project " +
                                                            "was closed but there was no time to index them). " +
                                                            "On project startup it's checked if these files need to be indexed")
  private val orphanDirtyFilesCountField = EventFields.Int("orphan_dirty_files_count",
                                                           "Number of dirty files from orphan queue (queue for files which were changed when " +
                                                           "corresponding projects were closed). " +
                                                           "On project startup it's checked if these files belong to project and if they need " +
                                                           "to be indexed.")

  private val partialInitialScanningScheduled = GROUP.registerEvent("partial.initial.scanning.scheduled",
                                                                    sourceOfScanningField,
                                                                    projectDirtyFilesCountField,
                                                                    orphanDirtyFilesCountField)

  internal enum class FullScanningReason(fieldName: String) {
    CodeCallerForbadeSkipping("code_caller_forbade_skipping"),
    RegistryForbadeSkipping("registry_forbade_skipping"),
    FilterIncompatibleAsNotLoadedFromDisc("filer_incompatible_as_not_loaded_from_disk"),
    FilterIncompatibleAsPersistentFilterIsDisabled("filer_incompatible_as_persistent_filter_is_disabled"),
    FilterIncompatibleAsFullScanningIsNotCompleted("filer_incompatible_as_full_scanning_is_not_completed"),
    FilterIncompatibleAsFilterIsInvalidated("filer_incompatible_as_filter_is_invalidated"),
    FilterIncompatibleAsAppIndexingRequestIdChangedSinceLastScanning("filer_incompatible_as_id_changed_since_last_scanning"),
    ;

    val field = EventFields.Boolean(fieldName)
  }

  internal enum class NotSeenIdsBasedFullScanningDecision(val valueName: String) {
    NoSkipDirtyFileQueuePintsToIncorrectPosition("no_skip_queue_incorrect_position"),
    NoSkipDirtyFileIdsWereMissed("no_skip_ids_were_missed"),
    DirtyFileIdsCompatibleWithFullScanningSkip("ids_compatible_with_full_scanning_skip")
  }

  private val notSeenIdsBasedFullScanningDecisionField =
    EventFields.Enum("not_seen_ids", NotSeenIdsBasedFullScanningDecision::class.java) { it.valueName }

  private val registeredIndexesWereCorruptedField = EventFields.Boolean("registered_indexes_corrupted")

  private val fullInitialScanningScheduled: VarargEventId

  init {
    val fields: MutableList<EventField<*>> = FullScanningReason.entries.map { it.field }.toMutableList()
    fields.add(notSeenIdsBasedFullScanningDecisionField)
    fields.add(sourceOfScanningField)
    fields.add(registeredIndexesWereCorruptedField)
    fields.add(projectDirtyFilesCountField)
    fields.add(orphanDirtyFilesCountField)
    fullInitialScanningScheduled = GROUP.registerVarargEvent("full.initial.scanning.scheduled", *fields.toTypedArray())
  }

  fun reportPartialInitialScanningScheduled(
    project: Project,
    sourceOfScanning: SourceOfScanning,
    projectDirtyFilesQueue: ProjectDirtyFilesQueue,
    orphanDirtyFilesCount: Int,
  ) {
    partialInitialScanningScheduled.log(project, sourceOfScanning, projectDirtyFilesQueue.fileIds.size, orphanDirtyFilesCount)
  }

  fun reportFullInitialScanningScheduled(
    project: Project,
    sourceOfScanning: SourceOfScanning,
    registeredIndexesWereCorrupted: Boolean,
    reasons: List<FullScanningReason>,
    notSeenIdsBasedFullScanningDecision: NotSeenIdsBasedFullScanningDecision,
    projectDirtyFilesQueue: ProjectDirtyFilesQueue,
    orphanDirtyFilesCount: Int,
  ) {
    fullInitialScanningScheduled.log(project) {
      for (reason in FullScanningReason.entries) {
        add(EventPair(reason.field, reasons.contains(reason)))
      }
      add(EventPair(notSeenIdsBasedFullScanningDecisionField, notSeenIdsBasedFullScanningDecision))
      add(EventPair(sourceOfScanningField, sourceOfScanning))
      add(EventPair(registeredIndexesWereCorruptedField, registeredIndexesWereCorrupted))
      add(EventPair(projectDirtyFilesCountField, projectDirtyFilesQueue.fileIds.size))
      add(EventPair(orphanDirtyFilesCountField, orphanDirtyFilesCount))
    }
  }
}

@Internal
internal class IndexesScanningSkipCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = InitialScanningSkipReporter.GROUP
}