// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.ActivityId
import com.intellij.history.core.changes.Change
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.ChangeVisitor
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.hours

@ApiStatus.Internal
interface ChangeList {
  fun nextId(): Long

  fun beginChangeSet()
  fun forceBeginChangeSet(): ChangeSet?
  fun addChange(c: Change)
  fun endChangeSet(name: @NlsContexts.Label String?, activityId: ActivityId?): ChangeSet?

  fun purgeObsolete(period: Long, intervalBetweenActivities: Long)
  fun purgeObsolete(period: Long): Unit = purgeObsolete(period, DEFAULT_INTERVAL_BETWEEN_ACTIVITIES_MILLISECONDS)

  fun iterChanges(): Iterable<ChangeSet>

  fun accept(v: ChangeVisitor) {
    try {
      for (change in iterChanges()) {
        change.accept(v)
      }
    }
    catch (_: ChangeVisitor.StopVisitingException) {
    }
    v.finished()
  }

  @get:TestOnly
  val changesInTests: List<ChangeSet> get() = iterChanges().toList()

  companion object {
    private val DEFAULT_INTERVAL_BETWEEN_ACTIVITIES_MILLISECONDS: Long = 12.hours.inWholeMilliseconds
  }
}
