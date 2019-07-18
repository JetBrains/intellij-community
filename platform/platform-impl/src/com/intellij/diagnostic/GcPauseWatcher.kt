/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

open class GcPauseWatcher {
  private val watchers = ManagementFactory.getGarbageCollectorMXBeans().map(::SingleCollectorWatcher)

  private inner class SingleCollectorWatcher(val bean: GarbageCollectorMXBean) {
    private var count = bean.collectionCount
    private var cumulativePauseTime = bean.collectionTime

    fun update() {
      val newCount = bean.collectionCount
      val newPauseTime = bean.collectionTime
      val currPauseDuration = newPauseTime - cumulativePauseTime
      if (newCount - count > 0 && currPauseDuration > 0) {
        recordGcPauseTime(bean.name, currPauseDuration)
      }
      count = newCount
      cumulativePauseTime = newPauseTime
    }
  }

  protected open fun recordGcPauseTime(name: String, currPauseDuration: Long) {
    if (!LoadingPhase.isStartupComplete()) {
      ParallelActivity.GC.record(StartUpMeasurer.getCurrentTime() - TimeUnit.MILLISECONDS.toNanos(currPauseDuration), null, null)
    }
  }

  init {
    Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(::checkForPauses, 0, SAMPLING_RATE_MS, TimeUnit.MILLISECONDS)
  }

  private fun checkForPauses() {
    watchers.forEach{it.update()}
  }

  companion object {
    private const val SAMPLING_RATE_MS = 50L  // ms. Should be set low enough that getting two pauses between samples is rare

    fun getInstance(): GcPauseWatcher = ServiceManager.getService(GcPauseWatcher::class.java)

    val LOG = Logger.getInstance(GcPauseWatcher::class.java)
  }
}