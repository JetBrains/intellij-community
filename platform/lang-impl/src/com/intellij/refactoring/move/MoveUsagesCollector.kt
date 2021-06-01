// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class MoveUsagesCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("move.refactoring", 2)

    @JvmField
    val MOVE_FILES_OR_DIRECTORIES = GROUP.registerEvent("move.files.or.directories")

    @JvmField
    val HANDLER_INVOKED = GROUP.registerEvent("handler.invoked", EventFields.Language,
                                              EventFields.Class("handler"))

  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}