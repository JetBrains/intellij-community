// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@ApiStatus.Internal
class FsQueryStatCounter {

  private val uniquePaths: ConcurrentMap<FsQuery, Instant> = ConcurrentHashMap<FsQuery, Instant>()

  data class FsQuery(
    val delegate: Measurer.DelegateType,
    val operation: Measurer.Operation,
    val success: Boolean,
    val path1: Path?,
    val path2: Path?,
  )

  fun repeatedTime(delegate: Measurer.DelegateType, path1: Path?, path2: Path?, operation: Measurer.Operation, startTime: Instant, endTime: Instant, success: Boolean): Duration? {
    val key = FsQuery(delegate, operation, success, path1, path2)
    val prevEndTime = uniquePaths.put(key, endTime)
    if (prevEndTime == null) return null
    return Duration.between(prevEndTime, startTime)
  }

}