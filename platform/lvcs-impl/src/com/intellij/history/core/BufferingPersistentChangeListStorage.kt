// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.diagnostic.rethrowControlFlowException
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
  private val openStorage: (Path) -> LocalHistoryStorage = ::LocalHistoryStorage,
) : ChangeListStorage {
  //TODO RC: use mmapped storage instead of old-school? Less freezes, and also more reliability
  private var storage: LocalHistoryStorage

  /**
   * The write buffer for [storage].
   * [writeNextSet] adds change sets to the end. [flushPending] writes them to [storage] from the start.
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

  /**
   * True when [storage] cannot be used.
   * Then [pendingChangeSets] keeps all change sets.
   */
  private var isPersistentStorageBroken = false

  /**
   * The change set that [flushPending] did not write.
   * If the same change set fails again, [flushPending] sets [isPersistentStorageBroken].
   */
  private var lastFailedChangeSet: ChangeSet? = null

  /**
   * True after [close].
   * Then [writeNextSet] drops the change sets, and a failed operation does not start a recovery.
   * [close] sets it under the lock of this storage and under [pendingSetsLock].
   */
  private var isClosed = false

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
    return openLocalHistoryStorage(storageDir, getCurrentFsTimestamp, unitTestMode, openStorage)
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

    synchronized(this) {
      if (isClosed) return
      Disposer.dispose(storage)
      try {
        storageDir.delete()
        storage = initStorage()
      }
      catch (ex: Throwable) {
        rethrowControlFlowException(ex)
        logError("cannot recreate storage", ex)
        isPersistentStorageBroken = true
      }
    }

    notifyStorageBroken()
  }

  @Synchronized
  override fun close(drop: Boolean) {
    if (!drop) {
      flushPending()
    }
    synchronized(pendingSetsLock) {
      if (drop) {
        pendingChangeSets.clear()
      }
      isClosed = true
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
      synchronized(this) { if (isPersistentStorageBroken) null else storage }?.force()
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
    if (isPersistentStorageBroken) return null
    return storage.readPreviousBlock(id, recursionGuard, ::handleError)
  }

  @Synchronized
  override fun iterate(): Iterator<ChangeSet> {
    flushPending()
    val pendingIterator = synchronized(pendingSetsLock) { pendingChangeSets.reversed() }.iterator()

    // The persistent iterator reads the last record now, under the lock.
    // So it does not return a pending change set that is written later.
    val persistentIterator = EagerNonRecursingReversedChangeSetIterator(::readPrevious)
    return iterator {
      yieldAll(pendingIterator)
      yieldAll(persistentIterator)
    }
  }

  override fun writeNextSet(changeSet: ChangeSet) {
    synchronized(pendingSetsLock) {
      if (isClosed) return
      pendingChangeSets.add(changeSet)
    }
  }

  @Synchronized
  private fun flushPending() {
    while (!isPersistentStorageBroken) {
      val changeSet = synchronized(pendingSetsLock) { pendingChangeSets.firstOrNull() } ?: return

      try {
        storage.writeStream(storage.createNextRecord(changeSet.timestamp), true).use { out ->
          changeSet.write(out)
        }
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        if (e is ClosedStorageException) return
        if (changeSet === lastFailedChangeSet) {
          logError("cannot write the change set ${changeSet.id} after the recovery, the change sets will be kept in memory", e)
          isPersistentStorageBroken = true
        }
        else {
          lastFailedChangeSet = changeSet
          handleError(e, null)
        }
        return
      }
      lastFailedChangeSet = null
      synchronized(pendingSetsLock) { pendingChangeSets.removeFirst() }
    }
  }

  /**
   * Purges the obsolete changesets from the *persistent* storage,
   * as non-persistent storage will almost never contain the obsolete data in the real world
   */
  @Synchronized
  override fun purge(period: Long, intervalBetweenActivities: Long) {
    // TODO: purge pendingChangeSets to guard against runaway memory consumption
    if (isPersistentStorageBroken) return
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
