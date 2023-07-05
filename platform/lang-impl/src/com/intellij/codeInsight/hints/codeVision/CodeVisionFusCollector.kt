// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class CodeVisionFusCollector: CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("daemon.codeVision", 1)


    val CODE_VISION_FINISHED = GROUP.registerEvent(
      "finished",
      EventFields.DurationMs,
      EventFields.Class("providerClass")
    )

    val ANNOTATION_LOADED = GROUP.registerEvent("vcsAnnotationLoaded", EventFields.DurationMs)
  }

  override fun getGroup(): EventLogGroup = GROUP
}