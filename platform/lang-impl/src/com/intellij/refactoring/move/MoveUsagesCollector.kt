// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language

internal object MoveUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("move.refactoring", 2)

  @JvmField
  val MOVE_FILES_OR_DIRECTORIES: EventId = GROUP.registerEvent("move.files.or.directories")

  @JvmField
  val HANDLER_INVOKED: EventId2<Language?, Class<*>?> = GROUP.registerEvent("handler.invoked", EventFields.Language,
                                                                            EventFields.Class("handler"))

  override fun getGroup(): EventLogGroup = GROUP
}