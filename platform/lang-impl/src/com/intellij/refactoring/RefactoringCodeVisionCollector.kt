// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object RefactoringCodeVisionCollector: CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("refactoring.code.vision", 1)

  enum class Refactorings { Rename, }

  private val REFACTORING_PERFORMED: EventId1<String> = GROUP.registerEvent("refactoring.performed",
                                                                            EventFields.String("refactoring", Refactorings.entries.map { it.name }))

  override fun getGroup(): EventLogGroup = GROUP

  fun refactoringPerformed(refactoring: Refactorings) {
    REFACTORING_PERFORMED.log(refactoring.name)
  }
}