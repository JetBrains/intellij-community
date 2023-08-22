// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.utils

import com.intellij.openapi.diagnostic.logger

class CuteBenchmarker(private val processName: String) {
  private val logger = logger<CuteBenchmarker>()

  private val segments = mutableMapOf<String, Long?>()

  fun start(segmentName: String) {
    if (segments[segmentName] != null) {
      logger.warn("Segment was already started: $processName.$segmentName")
      return
    }
    logger.info("Started: $processName.$segmentName")
    segments[segmentName] = System.nanoTime()
  }

  fun finish(segmentName: String) {
    if (segments[segmentName] == null) {
      logger.warn("Segment wasn't started: $processName.$segmentName")
      return
    }
    segments[segmentName] = null
    logger.info("Finished: $processName.$segmentName")
  }

  fun <T> timedSection(segmentName: String, action: () -> T): T {
    start(segmentName)
    val res = action()
    finish(segmentName)

    return res
  }
}