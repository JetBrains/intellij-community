// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.util.io.ClosedStorageException
import com.intellij.util.io.delete
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.text.DateFormat

private const val VERSION = 7
private const val STORAGE_FILE: @NonNls String = "changes"

internal class PersistentChangeListStorage(
  private val storageDir: Path,
  private val getUseWriteCache: () -> Boolean = { Registry.`is`("lvcs.store.write.cache.in.memory", true) },
  private val getCurrentFsTimestamp: () -> Long = { ManagingFS.getInstance().creationTimestamp },
  private val unitTestMode: Boolean = ApplicationManager.getApplication().isUnitTestMode(),
) : ChangeListStorage {
  //TODO RC: use mmapped storage instead of old-school? Less freezes, and also more reliability
  private val storage: LocalHistoryStorage

  /**
   * Write cache for the storage
   */
  private val pendingChangeSets = ArrayDeque<ChangeSet>()

  @get:VisibleForTesting
  var lastId: Long = 0
    private set

  private var isCompletelyBroken = false

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
    val path = storageDir.resolve(STORAGE_FILE)
    val fromScratch = unitTestMode && !Files.exists(path)

    var storage = LocalHistoryStorage(path)

    val fsTimestamp = getCurrentFsTimestamp()

    val storedVersion = storage.getVersion()
    val versionMismatch = storedVersion != VERSION
    val timestampMismatch = storage.getFSTimestamp() != fsTimestamp
    if (versionMismatch || timestampMismatch) {
      if (!fromScratch) {
        if (versionMismatch) {
          LocalHistoryLog.LOG.info("local history version mismatch (was: $storedVersion, expected: $VERSION), rebuilding...")
        }
        if (timestampMismatch) {
          LocalHistoryLog.LOG.info("FS has been rebuild, rebuilding local history...")
        }
        Disposer.dispose(storage)
        dropStorage()
        storage = LocalHistoryStorage(path)
      }
      storage.setVersion(VERSION)
      storage.setFSTimestamp(fsTimestamp)
    }

    lastId = storage.getLastId()
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

    val fullMsg = "Local history is broken " +
                  "(version:" + VERSION +
                  ", current timestamp: " + DateFormat.getDateTimeInstance().format(timestamp) +
                  ", storage timestamp: " + DateFormat.getDateTimeInstance().format(storageTimestamp) +
                  ", vfs timestamp: " + DateFormat.getDateTimeInstance().format(vfsTimestamp) +
                  ", path: " + storageDir +
                  ")\n" + message
    if (unitTestMode) {
      LocalHistoryLog.LOG.warn(fullMsg, e)
    }
    else {
      LocalHistoryLog.LOG.error(fullMsg, e)
    }

    Disposer.dispose(storage)
    try {
      dropStorage()
      initStorage()
    }
    catch (ex: Throwable) {
      LocalHistoryLog.LOG.error("cannot recreate storage", ex)
      synchronized(this) {
        isCompletelyBroken = true
      }
    }

    getLocalHistoryNotificationGroup()
      .createNotification(
        LocalHistoryBundle.message("notification.title.local.history.broken"),
        LocalHistoryBundle.message("notification.content.local.history.broken"),
        NotificationType.ERROR
      )
      .setDisplayId(LocalHistoryNotificationIdsHolder.STORAGE_CORRUPTED)
      .notify(null)
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
    try {
      flushPending()
      writeLastId()
      storage.force()
    }
    catch (e: IOException) {
      handleError(e, null)
    }
  }

  @Synchronized
  override fun nextId(): Long {
    return ++lastId
  }

  @Synchronized
  private fun readPrevious(id: Int, recursionGuard: IntSet): ChangeSetHolder? {
    if (isCompletelyBroken) return null

    var prevId = 0
    try {
      prevId = if (id == -1) {
        storage.getLastRecord()
      }
      else {
        storage.getPrevRecordSafely(id, recursionGuard)
      }
      if (prevId == 0) {
        return null
      }

      return storage.readBlock(prevId)
    }
    catch (e: Throwable) {
      var message: String? = null
      if (prevId != 0) {
        try {
          val prevOS = storage.getOffsetAndSize(prevId)
          val prevRecordTimestamp = storage.getTimestamp(prevId)
          val lastRecord = storage.getLastRecord()
          val lastOS = storage.getOffsetAndSize(lastRecord)
          val lastRecordTimestamp = storage.getTimestamp(lastRecord)

          message = "invalid record is: " + prevId + " offset: " + prevOS.first + " size: " + prevOS.second +
                    " (created " + DateFormat.getDateTimeInstance().format(prevRecordTimestamp) + ") " +
                    "last record is: " + lastRecord + " offset: " + lastOS.first + " size: " + lastOS.second +
                    " (created " + DateFormat.getDateTimeInstance().format(lastRecordTimestamp) + ")"
        }
        catch (e1: Exception) {
          message = "cannot retrieve more debug info: " + e1.message
        }
      }

      handleError(e, message)
      return null
    }
  }

  @Synchronized
  override fun iterate(): Iterator<ChangeSet> {
    flushPending()
    return object : Iterator<ChangeSet> {
      private val recursionGuard = IntOpenHashSet(1000)
      private var next = readPrevious(-1, recursionGuard)

      override fun hasNext(): Boolean = next != null

      override fun next(): ChangeSet {
        val result = next!!
        next = readPrevious(result.id, recursionGuard)
        return result.changeSet
      }
    }
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

    val recursionGuard = IntOpenHashSet(1000)
    try {
      val firstObsoleteId = findFirstObsoleteBlock(period, intervalBetweenActivities, recursionGuard)
      if (firstObsoleteId == 0) return

      var eachBlockId = firstObsoleteId
      while (eachBlockId != 0) {
        val changeSet = storage.readBlock(eachBlockId).changeSet
        for (each in changeSet.contentsToPurge) {
          each.release()
        }
        eachBlockId = storage.getPrevRecordSafely(eachBlockId, recursionGuard)
      }
      storage.deleteRecordsUpTo(firstObsoleteId)
    }
    catch (e: IOException) {
      handleError(e, null)
    }
  }

  @Throws(IOException::class)
  private fun findFirstObsoleteBlock(period: Long, intervalBetweenActivities: Long, recursionGuard: IntSet): Int {
    var prevTimestamp = 0L
    var length = 0L

    var last = storage.getLastRecord()
    while (last != 0) {
      val t = storage.getTimestamp(last)
      if (prevTimestamp == 0L) prevTimestamp = t

      val delta = prevTimestamp - t
      prevTimestamp = t

      // we sum only intervals between changes during one 'day' (intervalBetweenActivities) and add '1' between two 'days'
      length += if (delta < intervalBetweenActivities) delta else 1

      if (length >= period) return last

      last = storage.getPrevRecordSafely(last, recursionGuard)
    }

    return 0
  }
}

@Throws(IOException::class)
private fun LocalHistoryStorage.getPrevRecordSafely(id: Int, recursionGuard: IntSet): Int {
  recursionGuard.add(id)
  val prev = getPrevRecord(id)
  if (!recursionGuard.add(prev)) throw IOException("Recursive records found")
  return prev
}

@Throws(IOException::class)
private fun LocalHistoryStorage.readBlock(id: Int): ChangeSetHolder {
  readStream(id).use { `in` ->
    return ChangeSetHolder(id, ChangeSet(`in`))
  }
}