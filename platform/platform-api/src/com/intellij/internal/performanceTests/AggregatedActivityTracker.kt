// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performanceTests

import com.intellij.internal.performanceTests.ProjectInitializationDiagnostic.ActivityTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
class AggregatedActivityTracker(private val trackers: List<ActivityTracker>) : ActivityTracker {
  override fun activityFinished() {
    val exceptions = mutableListOf<Throwable>()
    for (tracker in trackers) {
      try {
        tracker.activityFinished()
      }
      catch (t: Throwable) {
        exceptions.add(t)
      }
    }
    if (exceptions.isNotEmpty()) {
      val first = exceptions[0]
      val remaining = exceptions.subList(1, exceptions.size)
      remaining.forEach(first::addSuppressed)
      throw first
    }
  }
}