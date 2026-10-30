// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.LocalHistoryStorageUtil.buildBrokenStorageMessage
import com.intellij.history.core.LocalHistoryStorageUtil.getPurgePeriod
import com.intellij.history.core.LocalHistoryStorageUtil.notifyStorageBroken
import com.intellij.history.core.LocalHistoryStorageUtil.openLocalHistoryStorage
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.util.io.ClosedStorageException
import com.intellij.util.io.delete
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private const val START_ID_LIMIT = 1L shl 62

internal class BufferingPersistentChangeListStorage(
  private val storageDir: Path,
  private val getCurrentFsTimestamp: () -> Long = { ManagingFS.getInstance().creationTimestamp },
  private val unitTestMode: Boolean = ApplicationManager.getApplication().isUnitTestMode(),
) : ChangeListStorage {
  //TODO RC: use mmapped storage instead of old-school? Less freezes, and also more reliability
  private var storage: LocalHistoryStorage

  /**
   * Write buffer for the storage
   * [pendingSetsLock] guards the buffer.
   */
  private val pendingChangeSets = ArrayDeque<ChangeSet>()
  private val pendingSetsLock = Any()

  /**
   * The last given ID.
   * The start value is a random number below [START_ID_LIMIT] and the IDs of one session are consecutive, so they are unique.
   *
   * [START_ID_LIMIT] limit exists to ensure that the counter doesn't overflow under normal operation.
   * By normal operation we mean that less than `Long.MAX_VALUE - START_ID_LIMIT` IDs are generated in one session.
   * This increases the risk of collision, but with the typical number of IDs well below tens of millions and the purge mechanism,
   * the risk is negligible.
   */
  private val lastIdRef = AtomicLong(Random.nextLong(START_ID_LIMIT))

  @Volatile
  private var isCompletelyBroken = false

  /**
   * True after [purgeOnce] ran [purge].
   */
  private var isPurged = false

  init {
    storage = initStorage()
  }

  @Synchronized
  @Throws(IOException::class)
  private fun initStorage(): LocalHistoryStorage {
    return openLocalHistoryStorage(storageDir, getCurrentFsTimestamp, unitTestMode)
  }

  private fun handleError(e: Throwable?, @NonNls message: @NonNls String?) {
    if (e is ClosedStorageException) {
      return
    }

    val vfsTimestamp = getCurrentFsTimestamp()
    val timestamp = System.currentTimeMillis()

    val storageTimestamp = try {
      storage.getFSTimestamp()
    }
    catch (ex: Exception) {
      LocalHistoryLog.LOG.warn("cannot read storage timestamp", ex)
      -1L
    }

    val fullMsg = buildBrokenStorageMessage(storageDir, timestamp, storageTimestamp, vfsTimestamp, message)
    logError(fullMsg, e)

    Disposer.dispose(storage)
    try {
      storageDir.delete()
      storage = initStorage()
    }
    catch (ex: Throwable) {
      LocalHistoryLog.LOG.error("cannot recreate storage", ex)
      synchronized(this) {
        isCompletelyBroken = true
      }
    }

    notifyStorageBroken()
  }

  @Synchronized
  override fun close(drop: Boolean) {
    if (!drop) {
      flushPending()
    }
    else {
      synchronized(pendingSetsLock) {
        pendingChangeSets.clear()
      }
    }
    Disposer.dispose(storage)
    if (drop) {
      storageDir.delete()
    }
  }

  override fun flush() {
    purgeOnce()
    try {
      flushPending()
      storage.force()
    }
    catch (e: IOException) {
      handleError(e, null)
    }
  }

  override fun nextId(): Long {
    return lastIdRef.incrementAndGet()
  }

  @Synchronized
  private fun readPrevious(id: Int, recursionGuard: IntSet): ChangeSetHolder? {
    if (isCompletelyBroken) return null
    return storage.readPreviousBlock(id, recursionGuard, ::handleError)
  }

  override fun iterate(): Iterator<ChangeSet> {
    flushPending()
    return EagerNonRecursingReversedChangeSetIterator(::readPrevious)
  }

  override fun writeNextSet(changeSet: ChangeSet) {
    if (isCompletelyBroken) return
    synchronized(pendingSetsLock) {
      pendingChangeSets.add(changeSet)
    }
  }

  private fun flushPending() {
    while (true) {
      synchronized(this) {
        val set = synchronized(pendingSetsLock) { pendingChangeSets.removeFirstOrNull() } ?: break
        doWriteNextSet(set)
      }
    }
  }

  @Synchronized
  private fun doWriteNextSet(changeSet: ChangeSet) {
    if (isCompletelyBroken) return

    try {
      storage.writeStream(storage.createNextRecord(changeSet.timestamp), true).use { out ->
        changeSet.write(out)
      }
    }
    catch (e: IOException) {
      handleError(e, null)
    }
  }

  /**
   * Purges the obsolete changesets from the *persistent* storage,
   * as non-persistent storage will almost never contain the obsolete data in the real world
   */
  @Synchronized
  override fun purge(period: Long, intervalBetweenActivities: Long) {
    if (isCompletelyBroken) return
    // unit tests often try to purge the changesets that were added in the same test
    if (unitTestMode) {
      flushPending()
    }

    try {
      storage.purgeObsoleteChangeSets(period, intervalBetweenActivities)
    }
    catch (e: IOException) {
      handleError(e, null)
    }
  }

  /**
   * Runs [purge] on the first call only. [getPurgePeriod] gives the period.
   * [flush] calls it, so the first flush purges the storage.
   */
  @Synchronized
  private fun purgeOnce() {
    if (isPurged) return
    isPurged = true
    LocalHistoryLog.LOG.debug("Purging local history...")
    purge(getPurgePeriod(), ChangeList.DEFAULT_INTERVAL_BETWEEN_ACTIVITIES_MILLISECONDS)
  }

  private fun logError(@NonNls message: String, e: Throwable?) {
    if (unitTestMode) {
      LocalHistoryLog.LOG.warn(message, e)
    }
    else {
      LocalHistoryLog.LOG.error(message, e)
    }
  }
}
