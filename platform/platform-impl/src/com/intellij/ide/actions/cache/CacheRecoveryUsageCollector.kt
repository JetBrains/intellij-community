// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal class CacheRecoveryUsageCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("cache.recovery.actions", 1)

    private val ACTION_ID_FIELD = StringEventField.ValidatedByCustomRule("action-id", "recovery-action")

    private val FROM_GUIDE_FIELD = BooleanEventField("from-guide")

    private val EVENT_ID by lazy {
      GROUP.registerVarargEvent(
        "perform",
        ACTION_ID_FIELD,
        FROM_GUIDE_FIELD
      )
    }

    fun recordRecoveryPerformedEvent(recoveryAction: RecoveryAction,
                                     fromGuide: Boolean,
                                     project: Project?) {
      EVENT_ID.log(
        project,
        ACTION_ID_FIELD with recoveryAction.actionKey,
        FROM_GUIDE_FIELD with fromGuide
      )
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}