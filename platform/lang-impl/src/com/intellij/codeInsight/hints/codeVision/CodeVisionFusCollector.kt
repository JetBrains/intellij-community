// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.daemon.impl.DaemonFusCollector
import com.intellij.internal.statistic.eventLog.events.EventFields
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object CodeVisionFusCollector {
  val CODE_VISION_FINISHED = DaemonFusCollector.GROUP.registerEvent(
    "codeVisionFinished",
    EventFields.DurationMs,
    EventFields.Class("providerClass")
  )
}