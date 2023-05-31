// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class RefactoringUsageCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("refactoring", 2)

    @JvmField
    val HANDLER: ClassEventField = EventFields.Class("handler")

    @JvmField
    val ELEMENT: ClassEventField = EventFields.Class("element")

    @JvmField
    val HANDLER_INVOKED: VarargEventId = GROUP.registerVarargEvent("handler.invoked", EventFields.Language,
                                                                   HANDLER, ELEMENT)

  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}