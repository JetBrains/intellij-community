// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object RenameCodeVisionCollector: CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("rename.code.vision", 1)
  private val CLICKED: EventId = GROUP.registerEvent("clicked")

  override fun getGroup(): EventLogGroup = GROUP

  fun inlayHintClicked() {
    CLICKED.log()
  }
}