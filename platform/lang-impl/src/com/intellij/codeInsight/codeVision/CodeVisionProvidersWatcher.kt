// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import java.util.concurrent.TimeUnit

class CodeVisionProvidersWatcher {

  companion object {
    const val Threshold: Int = 30
    const val MinThreshold: Int = 10
  }

  private val providersWaitingTime = mutableMapOf<String, Long>()

  fun reportProvider(groupId: String, time: Long) {
    val timeInSeconds = TimeUnit.SECONDS.convert(time, TimeUnit.NANOSECONDS)
    if (timeInSeconds >= Threshold) {
      providersWaitingTime[groupId] = if (providersWaitingTime.containsKey(groupId)) {
        (timeInSeconds + time) / 2
      }
      else {
        time
      }
    }

    val providerTime = providersWaitingTime[groupId]
    if (providerTime != null && providerTime < MinThreshold) {
      providersWaitingTime.remove(groupId)
    }
  }

  fun shouldConsiderProvider(groupId: String): Boolean {
    val time = providersWaitingTime[groupId]
    return time == null || time < Threshold
  }

  fun dropProvider(groupId: String) {
    providersWaitingTime.remove(groupId)
  }
}