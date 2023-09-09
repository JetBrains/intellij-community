// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object CodeVisionFusCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("daemon.code.vision", 2)

  val CODE_VISION_FINISHED = GROUP.registerEvent(
    "finished",
    EventFields.DurationMs,
    EventFields.Class("provider_class"),
    EventFields.Language
  )

  val ANNOTATION_LOADED = GROUP.registerEvent("vcs.annotation.loaded", EventFields.DurationMs)

  override fun getGroup(): EventLogGroup = GROUP
}