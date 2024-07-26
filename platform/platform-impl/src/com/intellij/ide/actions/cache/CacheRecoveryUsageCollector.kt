// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object CacheRecoveryUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("cache.recovery.actions", 6)

  private val ACTION_ID_FIELD =
    EventFields.String("action-id",
                       listOf(
                         "refresh",
                         "hammer",
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

  override fun getGroup(): EventLogGroup = GROUP
}