// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.delete
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.text.DateFormat
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.days

private const val DAYS_TO_KEEP_PROPERTY_KEY = "localHistory.daysToKeep"
private const val DEFAULT_DAYS_TO_KEEP = 5

/**
 * The storage constants, the storage init, the purge period, the log texts, and the notification of the persistent local history storage.
 * The record helpers, the change set iterator, and the purge are at the file level.
 */
internal object LocalHistoryStorageUtil {
  const val VERSION = 7
  const val STORAGE_FILE: @NonNls String = "changes"

  /**
   * Opens the storage in [storageDir].
   * When the storage version or the file system timestamp does not match, it deletes [storageDir] and opens a new storage.
   * In the unit test mode, it does not delete a storage that did not exist before.
   */
  @Throws(IOException::class)
  fun openLocalHistoryStorage(
    storageDir: Path,
    getCurrentFsTimestamp: () -> Long,
    unitTestMode: Boolean,
    openStorage: (Path) -> LocalHistoryStorage = ::LocalHistoryStorage,
  ): LocalHistoryStorage {
    val path = storageDir.resolve(STORAGE_FILE)
    val fromScratch = unitTestMode && !Files.exists(path)

    var storage = openStorage(path)

    val fsTimestamp = getCurrentFsTimestamp()

    try {
      val storedVersion = storage.version
      val versionMismatch = storedVersion != VERSION
      val timestampMismatch = storage.fsTimestamp != fsTimestamp
      if (versionMismatch || timestampMismatch) {
        if (!fromScratch) {
          if (versionMismatch) {
            LocalHistoryLog.LOG.info("local history version mismatch (was: $storedVersion, expected: $VERSION), rebuilding...")
          }
          if (timestampMismatch) {
            LocalHistoryLog.LOG.info("FS has been rebuild, rebuilding local history...")
          }
          Disposer.dispose(storage)
          storageDir.delete()
          storage = openStorage(path)
        }
        storage.version = VERSION
        storage.fsTimestamp = fsTimestamp
      }
    }
    catch (e: Exception) {
      Disposer.dispose(storage)
      throw e
    }

    return storage
  }

  /**
   * Returns the purge period in milliseconds. The `localHistory.daysToKeep` advanced setting gives it in days.
   * When the advanced setting is not registered, the period is [DEFAULT_DAYS_TO_KEEP] days.
   */
  fun getPurgePeriod(): Long = getDaysToKeep().days.inWholeMilliseconds

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

  /**
   * Returns the log text about a broken storage in [storageDir].
   * [message] gives more details about the failure.
   */
  fun buildBrokenStorageMessage(
    storageDir: Path,
    timestamp: Long,
    storageTimestamp: Long,
    vfsTimestamp: Long,
    @NonNls message: String?,
  ): @NonNls String =
    "Local history is broken " +
    "(version:" + VERSION +
    ", current timestamp: " + DateFormat.getDateTimeInstance().format(timestamp) +
    ", storage timestamp: " + DateFormat.getDateTimeInstance().format(storageTimestamp) +
    ", vfs timestamp: " + DateFormat.getDateTimeInstance().format(vfsTimestamp) +
    ", path: " + storageDir +
    ")\n" + message

  /**
   * Shows the notification that the local history storage is broken.
   */
  fun notifyStorageBrokenRebuild() {
    getLocalHistoryNotificationGroup()
      .createNotification(
        LocalHistoryBundle.message("notification.title.local.history.broken"),
        LocalHistoryBundle.message("notification.content.local.history.broken"),
        NotificationType.ERROR
      )
      .setDisplayId(LocalHistoryNotificationIdsHolder.STORAGE_CORRUPTED)
      .notify(null)
  }

  fun notifyStorageBrokenWithFallback(storageDir: Path) {
    val message = LocalHistoryBundle.message("notification.content.local.history.broken.completely", storageDir.absolutePathString())
    getLocalHistoryNotificationGroup()
      .createNotification(
        LocalHistoryBundle.message("notification.title.local.history.broken"),
        message,
        NotificationType.ERROR
      )
      .setDisplayId(LocalHistoryNotificationIdsHolder.STORAGE_CORRUPTED)
      .notify(null)
  }
}

@Throws(IOException::class)
internal fun LocalHistoryStorage.getPrevRecordSafely(id: Int, recursionGuard: IntSet): Int {
  recursionGuard.add(id)
  val prev = getPrevRecord(id)
  if (!recursionGuard.add(prev)) throw IOException("Recursive records found")
  return prev
}

@Throws(IOException::class)
internal fun LocalHistoryStorage.readBlock(id: Int): ChangeSetHolder {
  readStream(id).use { `in` ->
    return ChangeSetHolder(id, ChangeSet(`in`))
  }
}

/**
 * Returns the log text about the record [prevId], which the storage cannot read, and about the last record.
 * If the storage cannot give these details either, the text says so.
 */
internal fun LocalHistoryStorage.buildInvalidRecordMessage(prevId: Int): @NonNls String =
  try {
    val prevOS = getOffsetAndSize(prevId)
    val prevRecordTimestamp = getTimestamp(prevId)
    val lastRecord = getLastRecord()
    val lastOS = getOffsetAndSize(lastRecord)
    val lastRecordTimestamp = getTimestamp(lastRecord)

    "invalid record is: " + prevId + " offset: " + prevOS.first + " size: " + prevOS.second +
    " (created " + DateFormat.getDateTimeInstance().format(prevRecordTimestamp) + ") " +
    "last record is: " + lastRecord + " offset: " + lastOS.first + " size: " + lastOS.second +
    " (created " + DateFormat.getDateTimeInstance().format(lastRecordTimestamp) + ")"
  }
  catch (e1: Exception) {
    "cannot retrieve more debug info: " + e1.message
  }

/**
 * Reads the record before [id], or the last record when [id] is -1.
 * Returns null when there is no such record.
 * On a failure, calls [onError] with the details about the invalid record, and returns null.
 */
internal inline fun LocalHistoryStorage.readPreviousBlock(
  id: Int,
  recursionGuard: IntSet,
  onError: (e: Throwable, message: String?) -> Unit,
): ChangeSetHolder? {
  var prevId = 0
  try {
    prevId = if (id == -1) lastRecord else getPrevRecordSafely(id, recursionGuard)
    if (prevId == 0) return null
    return readBlock(prevId)
  }
  catch (e: Throwable) {
    onError(e, if (prevId != 0) buildInvalidRecordMessage(prevId) else null)
    return null
  }
}

/**
 * Iterates over the change sets of the storage from the last record to the first one.
 * [readPrevious] returns the record before the given one, the last record for -1, or null at the end.
 * The constructor reads the last record, so the iterator does not return later change sets.
 * All reads share one recursion guard, so the iterator stops at a recursive record chain.
 */
internal class EagerNonRecursingReversedChangeSetIterator(
  private val readPrevious: (id: Int, recursionGuard: IntSet) -> ChangeSetHolder?,
) : Iterator<ChangeSet> {
  private val recursionGuard = IntOpenHashSet(1000)
  private var next = readPrevious(-1, recursionGuard)

  override fun hasNext(): Boolean = next != null

  override fun next(): ChangeSet {
    val result = next!!
    next = readPrevious(result.id, recursionGuard)
    return result.changeSet
  }
}

/**
 * Removes the obsolete change sets from the storage and releases their contents.
 * [ChangeListStorage.purge] tells which change sets are obsolete.
 */
@Throws(IOException::class)
internal fun LocalHistoryStorage.purgeObsoleteChangeSets(period: Long, intervalBetweenActivities: Long) {
  val recursionGuard = IntOpenHashSet(1000)
  val firstObsoleteId = findFirstObsoleteBlock(period, intervalBetweenActivities, recursionGuard)
  if (firstObsoleteId == 0) return

  var eachBlockId = firstObsoleteId
  while (eachBlockId != 0) {
    val changeSet = readBlock(eachBlockId).changeSet
    for (each in changeSet.contentsToPurge) {
      each.release()
    }
    eachBlockId = getPrevRecordSafely(eachBlockId, recursionGuard)
  }
  deleteRecordsUpTo(firstObsoleteId)
}

@Throws(IOException::class)
private fun LocalHistoryStorage.findFirstObsoleteBlock(period: Long, intervalBetweenActivities: Long, recursionGuard: IntSet): Int {
  var prevTimestamp = 0L
  var length = 0L

  var last = lastRecord
  while (last != 0) {
    val t = getTimestamp(last)
    if (prevTimestamp == 0L) prevTimestamp = t

    val delta = prevTimestamp - t
    prevTimestamp = t

    // we sum only intervals between changes during one 'day' (intervalBetweenActivities) and add '1' between two 'days'
    length += if (delta < intervalBetweenActivities) delta else 1

    if (length >= period) return last

    last = getPrevRecordSafely(last, recursionGuard)
  }

  return 0
}
