// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.platform.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.platform.diagnostic.telemetry.impl.writeInProtobufFormat

internal suspend fun sendStartupTraceUsingOtlp(unsortedActivities: List<ActivityImpl>, endpoint: String) {
  val startTimeUnixNanoDiff = StartUpMeasurer.getStartTimeUnixNanoDiff()
  writeInProtobufFormat(startTimeUnixNanoDiff = startTimeUnixNanoDiff,
                        activities = unsortedActivities.sortedWith(itemComparator),
                        endpoint = endpoint)
}
