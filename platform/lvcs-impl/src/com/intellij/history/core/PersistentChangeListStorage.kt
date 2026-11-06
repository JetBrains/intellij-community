// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.LocalHistoryStorageUtil.buildBrokenStorageMessage
import com.intellij.history.core.LocalHistoryStorageUtil.getPurgePeriod
import com.intellij.history.core.LocalHistoryStorageUtil.notifyStorageBrokenRebuild
import com.intellij.history.core.LocalHistoryStorageUtil.openLocalHistoryStorage
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.util.io.ClosedStorageException
import com.intellij.util.io.delete
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

internal class PersistentChangeListStorage(
  private val storageDir: Path,
  private val getUseWriteCache: () -> Boolean = { Registry.`is`("lvcs.store.write.cache.in.memory", true) },
  private val getCurrentFsTimestamp: () -> Long = { ManagingFS.getInstance().creationTimestamp },
  private val unitTestMode: Boolean = ApplicationManager.getApplication().isUnitTestMode(),
) : ChangeListStorage {
  //TODO RC: use mmapped storage instead of old-school? Less freezes, and also more reliability
  private var storage: LocalHistoryStorage

  /**
   * Write cache for the storage
   */
  private val pendingChangeSets = ArrayDeque<ChangeSet>()

  private val lastIdRef = AtomicLong()
  @get:VisibleForTesting
  val lastId: Long get() = lastIdRef.get()

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
  private fun dropStorage() {
    pendingChangeSets.clear()
    storageDir.delete()
  }

  @Synchronized
  @Throws(IOException::class)
  private fun initStorage(): LocalHistoryStorage {
    val storage = openLocalHistoryStorage(storageDir, getCurrentFsTimestamp, unitTestMode)
    lastIdRef.set(storage.getLastId())
    return storage
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
    if (unitTestMode) {
      LocalHistoryLog.LOG.warn(fullMsg, e)
    }
    else {
      LocalHistoryLog.LOG.error(fullMsg, e)
    }

    Disposer.dispose(storage)
    try {
      dropStorage()
      storage = initStorage()
    }
    catch (ex: Throwable) {
      LocalHistoryLog.LOG.error("cannot recreate storage", ex)
      synchronized(this) {
        isCompletelyBroken = true
      }
    }

    notifyStorageBrokenRebuild()
  }

  override fun close(drop: Boolean) {
    if (!drop) {
      flushPending()
      writeLastId()
    }
    Disposer.dispose(storage)
    if (drop) {
      dropStorage()
    }
  }

  override fun flush() {
    purgeOnce()
    try {
      flushPending()
      writeLastId()
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

  @Synchronized
  override fun writeNextSet(changeSet: ChangeSet) {
    if (isCompletelyBroken) return
    if (getUseWriteCache()) {
      pendingChangeSets.add(changeSet)
    }
    else {
      doWriteNextSet(changeSet)
      writeLastId()
    }
  }

  private fun flushPending() {
    while (true) {
      synchronized(this) {
        val set = pendingChangeSets.removeFirstOrNull() ?: break
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

  @Synchronized
  private fun writeLastId() {
    if (isCompletelyBroken) return

    try {
      storage.setLastId(lastId)
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
}
