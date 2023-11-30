// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object CacheRecoveryUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("cache.recovery.actions", 5)

  private val ACTION_ID_FIELD =
    EventFields.String("action-id",
                       listOf(
                         "refresh",
                         "hammer",
                         "recover-from-log",
                         "reindex",
                         "drop-shared-index",
                         "rescan",
                         "stop",
                         "reload-workspace-model"
                       )
    )

  private val FROM_GUIDE_FIELD = EventFields.Boolean("from-guide")

  private val EVENT_ID = GROUP.registerVarargEvent(
    "perform",
    ACTION_ID_FIELD,
    FROM_GUIDE_FIELD
  )

  fun recordRecoveryPerformedEvent(recoveryAction: RecoveryAction,
                                   fromGuide: Boolean,
                                   project: Project?) {
    EVENT_ID.log(
      project,
      ACTION_ID_FIELD with recoveryAction.actionKey,
      FROM_GUIDE_FIELD with fromGuide
    )
  }

  fun recordGuideStoppedEvent(project: Project) {
    EVENT_ID.log(
      project,
      ACTION_ID_FIELD with "stop",
      FROM_GUIDE_FIELD with true
    )
  }

  private val ON_VFS_INIT_FIELD = EventFields.Boolean("on_vfs_init")
  private val RECOVERY_TIME_MILLIS = EventFields.Long("recovery_time_millis")
  private val RECOVERED_FILES_COUNT = EventFields.Int("recovered_files")
  private val BOTCHED_FILES_COUNT = EventFields.Int("botched_files")
  private val DUPLICATE_CHILDREN_LOST_COUNT = EventFields.Int("duplicate_children_lost")
  private val DUPLICATE_CHILDREN_DEDUPLICATED_COUNT = EventFields.Int("duplicate_children_deduplicated")
  private val RECOVERED_ATTRIBUTES_COUNT = EventFields.Long("recovered_attributes")
  private val DROPPED_ATTRIBUTES_COUNT = EventFields.Long("dropped_attributes")
  private val RECOVERED_CONTENTS_COUNT = EventFields.Int("recovered_contents")
  private val LOST_CONTENTS_COUNT = EventFields.Int("lost_contents")

  private val RECOVERY_FROM_LOG_STARTED_EVENT = GROUP.registerEvent("recovery.from.log.started",
                                                                    ON_VFS_INIT_FIELD)

  private val RECOVERY_FROM_LOG_FINISHED_EVENT = GROUP.registerVarargEvent("recovery.from.log.finished",
                                                                           ON_VFS_INIT_FIELD,
                                                                           RECOVERY_TIME_MILLIS,
                                                                           RECOVERED_FILES_COUNT,
                                                                           BOTCHED_FILES_COUNT,
                                                                           DUPLICATE_CHILDREN_LOST_COUNT,
                                                                           DUPLICATE_CHILDREN_DEDUPLICATED_COUNT,
                                                                           RECOVERED_ATTRIBUTES_COUNT,
                                                                           DROPPED_ATTRIBUTES_COUNT,
                                                                           RECOVERED_CONTENTS_COUNT,
                                                                           LOST_CONTENTS_COUNT)

  fun recordRecoveryFromLogStarted(onVfsInit: Boolean) {
    RECOVERY_FROM_LOG_STARTED_EVENT.log(onVfsInit)
  }

  fun recordRecoveryFromLogFinishedEvent(onVfsInit: Boolean,
                                         recoveryTimeMillis: Long,
                                         recoveredFilesCount: Int,
                                         botchedFilesCount: Int,
                                         duplicateChildrenLostCount: Int,
                                         duplicateChildrenDeduplicatedCount: Int,
                                         recoveredAttributesCount: Long,
                                         droppedAttributesCount: Long,
                                         recoveredContentsCount: Int,
                                         lostContentsCount: Int) {
    RECOVERY_FROM_LOG_FINISHED_EVENT.log(
      ON_VFS_INIT_FIELD with onVfsInit,
      RECOVERY_TIME_MILLIS with recoveryTimeMillis,
      RECOVERED_FILES_COUNT with recoveredFilesCount,
      BOTCHED_FILES_COUNT with botchedFilesCount,
      DUPLICATE_CHILDREN_LOST_COUNT with duplicateChildrenLostCount,
      DUPLICATE_CHILDREN_DEDUPLICATED_COUNT with duplicateChildrenDeduplicatedCount,
      RECOVERED_ATTRIBUTES_COUNT with recoveredAttributesCount,
      DROPPED_ATTRIBUTES_COUNT with droppedAttributesCount,
      RECOVERED_CONTENTS_COUNT with recoveredContentsCount,
      LOST_CONTENTS_COUNT with lostContentsCount
    )
  }

  override fun getGroup(): EventLogGroup = GROUP
}