// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.ActivityId
import com.intellij.history.core.changes.Change
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.days

private const val DAYS_TO_KEEP_PROPERTY_KEY = "localHistory.daysToKeep"
private const val DEFAULT_DAYS_TO_KEEP = 5

internal class ChangeListImpl(private val storage: ChangeListStorage) : ChangeList {
  private val purged = AtomicBoolean(false)

  private var changeSetDepth = 0
  private var currentChangeSet: ChangeSet? = null

  @Synchronized
  override fun nextId(): Long = storage.nextId()

  @Synchronized
  override fun addChange(c: Change) {
    assert(changeSetDepth != 0)
    currentChangeSet!!.addChange(c)
  }

  @Synchronized
  override fun beginChangeSet() {
    changeSetDepth++
    if (changeSetDepth > 1) return

    doBeginChangeSet()
  }

  private fun doBeginChangeSet() {
    currentChangeSet = ChangeSet(nextId(), Clock.getTime())
  }

  @Synchronized
  override fun forceBeginChangeSet(): ChangeSet? {
    val lastChangeSet = if (changeSetDepth > 0) doEndChangeSet(null, null) else null

    changeSetDepth++
    doBeginChangeSet()
    return lastChangeSet
  }

  @Synchronized
  override fun endChangeSet(name: @NlsContexts.Label String?, activityId: ActivityId?): ChangeSet? {
    LocalHistoryLog.LOG.assertTrue(changeSetDepth > 0, "not balanced 'begin/end-change set' calls")

    changeSetDepth--
    if (changeSetDepth > 0) return null

    return doEndChangeSet(name, activityId)
  }

  private fun doEndChangeSet(name: @NlsContexts.Label String?, activityId: ActivityId?): ChangeSet? {
    if (currentChangeSet!!.isEmpty) {
      currentChangeSet = null
      return null
    }

    val lastChangeSet = currentChangeSet
    lastChangeSet!!.name = name
    lastChangeSet.activityId = activityId
    lastChangeSet.lock()
    storage.writeNextSet(lastChangeSet)

    currentChangeSet = null

    return lastChangeSet
  }

  // todo synchronization issue: changeset may me modified while being iterated
  override fun iterChanges(): Iterable<ChangeSet> {
    return Iterable {
      val currentSet = synchronized(this@ChangeListImpl) {
        currentChangeSet
      }
      object : Iterator<ChangeSet> {
        private val starterCurrentSet = AtomicReference(currentSet)
        private val storageIterator: Iterator<ChangeSet> by lazy {
          storage.iterate()
        }

        override fun hasNext(): Boolean {
          return starterCurrentSet.get() != null || storageIterator.hasNext()
        }

        override fun next(): ChangeSet {
          val current = starterCurrentSet.getAndSet(null)
          return current
                 ?: storageIterator.nextOrNull()
                 ?: error("No more changesets available")
        }
      }
    }
  }

  override fun purgeObsolete(period: Long, intervalBetweenActivities: Long) {
    storage.purge(period, intervalBetweenActivities)
  }

  suspend fun flush() {
    withContext(Dispatchers.IO) {
      if (purged.compareAndSet(false, true)) {
        val daysToKeep = getDaysToKeep()
        val period = daysToKeep.days.inWholeMilliseconds
        LocalHistoryLog.LOG.debug("Purging local history...")
        purgeObsolete(period)
      }
      checkCanceled()

      storage.flush()
    }
  }

  private fun getDaysToKeep(): Int =
    try {
      AdvancedSettings.getInt(DAYS_TO_KEEP_PROPERTY_KEY)
    }
    catch (e: IllegalArgumentException) {
      // The advanced setting may not be registered (e.g. in test environments where the lvcs-impl extensions aren't loaded).
      LocalHistoryLog.LOG.warn("Advanced setting '$DAYS_TO_KEEP_PROPERTY_KEY' is not registered, using default value $DEFAULT_DAYS_TO_KEEP",
                               e)
      DEFAULT_DAYS_TO_KEEP
    }

  @Synchronized
  fun close(drop: Boolean) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      LocalHistoryLog.LOG.assertTrue(currentChangeSet == null || currentChangeSet!!.isEmpty,
                                     "current changes won't be saved: $currentChangeSet")
    }
    storage.close(drop)
  }
}

private fun <T> Iterator<T>.nextOrNull(): T? {
  return if (hasNext()) next() else null
}