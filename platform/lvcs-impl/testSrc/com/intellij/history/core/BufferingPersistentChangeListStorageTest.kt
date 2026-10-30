// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.ContentChange
import com.intellij.history.core.changes.CreateFileChange
import com.intellij.openapi.util.Clock
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.impl.IndexDebugProperties
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.storage.AbstractStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@TestApplication //needed for recovery since it wants notifications
internal class BufferingPersistentChangeListStorageTest {
  @TempDir
  private lateinit var tempDir: Path

  private lateinit var storage: BufferingPersistentChangeListStorage

  private fun createStorage(openStorage: (Path) -> LocalHistoryStorage = ::LocalHistoryStorage) = BufferingPersistentChangeListStorage(
    storageDir = tempDir.resolve("storage"),
    getCurrentFsTimestamp = { 0 },
    unitTestMode = true,
    openStorage = openStorage,
  )

  @BeforeEach
  fun setUp() {
    Clock.setTime(NOW)
    storage = createStorage()
  }

  @AfterEach
  fun tearDown() {
    storage.close(true)
    Clock.reset()
  }

  @Test
  fun `iterate returns empty iterator when nothing written`() {
    val iterator = storage.iterate()
    assertThat(iterator.hasNext()).isFalse()
  }

  @Test
  fun `write and iterate single changeSet`() {
    val changeSet = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(changeSet)

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(changeSet.id)
  }

  @Test
  fun `write and iterate multiple changeSets in reverse order`() {
    val cs1 = createChangeSet(storage.nextId(), 1000L)
    val cs2 = createChangeSet(storage.nextId(), 2000L)
    val cs3 = createChangeSet(storage.nextId(), 3000L)

    storage.writeNextSet(cs1)
    storage.writeNextSet(cs2)
    storage.writeNextSet(cs3)

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(3)
    // iterate returns from last to first
    assertThat(result[0].id).isEqualTo(cs3.id)
    assertThat(result[1].id).isEqualTo(cs2.id)
    assertThat(result[2].id).isEqualTo(cs1.id)
  }

  @Test
  fun `flush persists data that survives reopen`() {
    val cs1 = createChangeSet(storage.nextId(), 1000L)
    val cs2 = createChangeSet(storage.nextId(), 2000L)
    storage.writeNextSet(cs1)
    storage.writeNextSet(cs2)
    storage.flush()

    storage.close(false)
    storage = createStorage()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(2)
    assertThat(result[0].id).isEqualTo(cs2.id)
    assertThat(result[1].id).isEqualTo(cs1.id)
  }

  @Test
  fun `close with drop removes all data`() {
    val cs = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs)
    storage.flush()

    storage.close(true)
    storage = createStorage()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).isEmpty()
  }

  @Test
  fun `close without drop preserves data`() {
    val cs = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs)

    storage.close(false)
    storage = createStorage()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(cs.id)
  }

  @Test
  fun `purge removes old records`() {
    val cs1 = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs1)

    val cs2 = createChangeSet(storage.nextId(), 5000L)
    storage.writeNextSet(cs2)

    storage.flush()

    // purge with a period shorter than the time span should remove old records
    storage.purge(1L, 1000L)

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(cs2.id)
  }

  @Test
  fun `purge does nothing when all records are within period`() {
    val cs1 = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs1)
    val cs2 = createChangeSet(storage.nextId(), 2000L)
    storage.writeNextSet(cs2)

    storage.flush()

    // purge with a very large period should keep all records
    storage.purge(Long.MAX_VALUE, 10_000L)

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(2)
  }

  @Test
  fun `iterate preserves changeSet content after round-trip`() {
    val cs = createChangeSet(storage.nextId(), 1000L, "test/path.txt")
    storage.writeNextSet(cs)
    storage.flush()

    storage.close(false)
    storage = createStorage()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(cs.id)
    assertThat(result[0].timestamp).isEqualTo(cs.timestamp)
    assertThat(result[0].changes).hasSize(1)
  }

  @Test
  fun `multiple write and flush cycles`() {
    val cs1 = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs1)
    storage.flush()

    val cs2 = createChangeSet(storage.nextId(), 2000L)
    storage.writeNextSet(cs2)
    storage.flush()

    val cs3 = createChangeSet(storage.nextId(), 3000L)
    storage.writeNextSet(cs3)
    storage.flush()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(3)
    assertThat(result[0].id).isEqualTo(cs3.id)
    assertThat(result[1].id).isEqualTo(cs2.id)
    assertThat(result[2].id).isEqualTo(cs1.id)
  }

  @Test
  fun `write serves data before flush`() {
    val cs1 = createChangeSet(storage.nextId(), 1000L)
    val cs2 = createChangeSet(storage.nextId(), 2000L)
    storage.writeNextSet(cs1)
    storage.writeNextSet(cs2)

    // data should be readable from the in-memory write buffer even without flushing
    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(2)
    assertThat(result[0].id).isEqualTo(cs2.id)
    assertThat(result[1].id).isEqualTo(cs1.id)
  }

  @Test
  fun `flush moves data from write buffer to persistent storage`() {
    val cs = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs)

    // before flush, data is in write buffer; after reopen without flush it would be lost
    storage.flush()

    storage.close(false)
    storage = createStorage()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(cs.id)
  }

  @Test
  fun `unflushed write buffer data is flushed on close without drop`() {
    val cs = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs)

    // close without drop should flush pending data
    storage.close(false)
    storage = createStorage()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(cs.id)
  }

  @Test
  fun `IDs of one session are consecutive`() {
    val id = storage.nextId()
    assertThat(storage.nextId()).isEqualTo(id + 1)
    assertThat(storage.nextId()).isEqualTo(id + 2)
  }

  @Test
  fun `new session does not repeat IDs after the storage is dropped`() {
    val id = storage.nextId()
    storage.close(/*drop: */true)

    storage = createStorage()
    assertThat(storage.nextId()).isNotEqualTo(id)
  }

  @Test
  fun `storage recovers when the data table was not closed`() {
    storage.writeNextSet(createChangeSet(storage.nextId(), 1000L))
    storage.flush()
    storage.close(/*drop: */false)
    Files.write(storageDataPath(), ByteArray(Int.SIZE_BYTES), WRITE)

    val previousDebugValue = IndexDebugProperties.DEBUG
    IndexDebugProperties.DEBUG = true
    val changeSets = try {
      storage = createStorage()
      // the storage opens its files on the first use
      storage.iterate().asSequence().toList()
    }
    finally {
      IndexDebugProperties.DEBUG = previousDebugValue
    }

    assertThat(changeSets)
      .describedAs("The recovered storage must not contain data from the damaged table")
      .isEmpty()

    val recoveredChangeSet = createChangeSet(storage.nextId(), 2000L)
    storage.writeNextSet(recoveredChangeSet)
    storage.flush()
    storage.close(/*drop: */false)
    storage = createStorage()
    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The recovered storage must persist new change sets")
      .extracting<Long> { it.id }
      .containsExactly(recoveredChangeSet.id)
  }

  @Test
  fun `storage accepts writes after recovery from a recursive record chain`() {
    val originalChangeSet = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(originalChangeSet)
    storage.flush()
    storage.close(/*drop: */false)
    makeLastRecordPointToItself()
    storage = createStorage()

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("Reading a recursive record chain must start storage recovery")
      .extracting<Long> { it.id }
      .containsExactly(originalChangeSet.id)

    val recoveredChangeSet = createChangeSet(storage.nextId(), 2000L)
    storage.writeNextSet(recoveredChangeSet)
    storage.flush()

    assertThat(storage.iterate().asSequence().toList())
      .`as`("The recovered storage must accept new changes")
      .extracting<Long> { it.id }
      .containsExactly(recoveredChangeSet.id)
  }

  @Test
  fun `pending change sets survive a successful recovery`() {
    val originalChangeSet = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(originalChangeSet)
    storage.flush()
    storage.close(/*drop: */false)
    makeLastRecordPointToItself()
    storage = createStorage()

    val iterator = storage.iterate()
    val pendingChangeSet = createChangeSet(storage.nextId(), 2000L)
    storage.writeNextSet(pendingChangeSet)
    assertThat(iterator.asSequence().toList())
      .describedAs("Reading a recursive record chain must start storage recovery")
      .extracting<Long> { it.id }
      .containsExactly(originalChangeSet.id)

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The recovery must keep the pending change sets")
      .extracting<Long> { it.id }
      .containsExactly(pendingChangeSet.id)

    storage.close(/*drop: */false)
    storage = createStorage()
    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The recovered storage must persist the pending change sets")
      .extracting<Long> { it.id }
      .containsExactly(pendingChangeSet.id)
  }

  @Test
  fun `storage keeps change sets in memory when recovery fails`() {
    val originalChangeSet = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(originalChangeSet)
    storage.flush()
    storage.close(/*drop: */false)
    makeLastRecordPointToItself()
    var openCount = 0
    storage = createStorage { path ->
      if (++openCount > 1) throw IOException("The test does not allow to open the storage again")
      LocalHistoryStorage(path)
    }

    val iterator = storage.iterate()
    val pendingChangeSet = createChangeSet(storage.nextId(), NOW - 2000L)
    storage.writeNextSet(pendingChangeSet)
    assertThat(iterator.asSequence().toList())
      .describedAs("Reading a recursive record chain must start storage recovery")
      .extracting<Long> { it.id }
      .containsExactly(originalChangeSet.id)
    assertThat(openCount)
      .describedAs("The recovery must try to open the storage again")
      .isEqualTo(2)

    val nextChangeSet = createChangeSet(storage.nextId(), NOW - 1000L)
    storage.writeNextSet(nextChangeSet)
    storage.flush()
    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The broken storage must keep the change sets in memory")
      .extracting<Long> { it.id }
      .containsExactly(nextChangeSet.id, pendingChangeSet.id)
  }

  @Test
  fun `closed storage drops new change sets and keeps its files`() {
    val changeSet = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(changeSet)
    val closedStorage = storage
    closedStorage.close(/*drop: */false)

    val lateChangeSet = createChangeSet(closedStorage.nextId(), 2000L)
    closedStorage.writeNextSet(lateChangeSet)
    assertThat(closedStorage.iterate().asSequence().toList())
      .describedAs("A closed storage must not keep the change sets that are written after close")
      .extracting<Long> { it.id }
      .doesNotContain(lateChangeSet.id)

    storage = createStorage()
    assertThat(storage.iterate().asSequence().toList())
      .describedAs("A call after close must not recreate the storage")
      .extracting<Long> { it.id }
      .containsExactly(changeSet.id)
  }

  @Test
  fun `storage keeps change sets in memory when it cannot be opened`() {
    storage.close(/*drop: */true)
    var openCount = 0
    storage = createStorage {
      openCount++
      throw IOException("The test does not allow to open the storage")
    }

    val cs1 = createChangeSet(storage.nextId(), NOW - 2000L)
    storage.writeNextSet(cs1)
    val cs2 = createChangeSet(storage.nextId(), NOW - 1000L)
    storage.writeNextSet(cs2)
    storage.flush()

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The broken storage must keep the change sets in memory")
      .extracting<Long> { it.id }
      .containsExactly(cs2.id, cs1.id)
    assertThat(openCount)
      .describedAs("The broken storage must not try to open the storage again")
      .isEqualTo(1)

    storage.close(/*drop: */false)
    storage = createStorage()
    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The change sets of a broken storage must not survive a reopen")
      .isEmpty()
  }

  @Test
  fun `flush of a broken storage removes change sets older than one hour`() {
    breakStorage()
    val obsoleteChangeSet = createChangeSet(storage.nextId(), NOW - 2.hours.inWholeMilliseconds)
    storage.writeNextSet(obsoleteChangeSet)
    val keptChangeSet = createChangeSet(storage.nextId(), NOW - 30.minutes.inWholeMilliseconds)
    storage.writeNextSet(keptChangeSet)

    storage.flush()

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The flush must remove only the change sets that are older than one hour")
      .extracting<Long> { it.id }
      .containsExactly(keptChangeSet.id)
  }

  @Test
  fun `flush of a broken storage releases the contents of the removed change sets`() {
    breakStorage()
    val obsoleteContent = ReleaseCountingContent()
    storage.writeNextSet(createContentChangeSet(storage.nextId(), NOW - 2.hours.inWholeMilliseconds, obsoleteContent))
    val keptContent = ReleaseCountingContent()
    storage.writeNextSet(createContentChangeSet(storage.nextId(), NOW - 30.minutes.inWholeMilliseconds, keptContent))

    storage.flush()

    assertThat(obsoleteContent.releaseCount)
      .describedAs("The flush must release the contents of a removed change set")
      .isEqualTo(1)
    assertThat(keptContent.releaseCount)
      .describedAs("The flush must not release the contents of a kept change set")
      .isZero()
  }

  @Test
  fun `flush of a working storage writes old change sets`() {
    val oldChangeSet = createChangeSet(storage.nextId(), NOW - 2.hours.inWholeMilliseconds)
    storage.writeNextSet(oldChangeSet)

    storage.flush()

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The flush of a working storage must write old change sets, not remove them")
      .extracting<Long> { it.id }
      .containsExactly(oldChangeSet.id)
  }

  @Test
  fun `write buffer purge does not block a concurrent write`() {
    breakStorage()
    val concurrentChangeSet = createChangeSet(storage.nextId(), NOW)
    val obsoleteContent = ReleaseCountingContent {
      val writer = Thread { storage.writeNextSet(concurrentChangeSet) }
      writer.start()
      writer.join(10_000)
      assertThat(writer.isAlive)
        .describedAs("writeNextSet must not wait for the purge")
        .isFalse()
    }
    storage.writeNextSet(createContentChangeSet(storage.nextId(), NOW - 2.hours.inWholeMilliseconds, obsoleteContent))
    val keptChangeSet = createChangeSet(storage.nextId(), NOW - 30.minutes.inWholeMilliseconds)
    storage.writeNextSet(keptChangeSet)

    storage.flush()

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The purge must keep the change set that is written during the purge")
      .extracting<Long> { it.id }
      .containsExactly(concurrentChangeSet.id, keptChangeSet.id)
  }

  @Test
  fun `purge of a broken storage keeps the write buffer`() {
    breakStorage()
    val oldChangeSet = createChangeSet(storage.nextId(), NOW - 2.hours.inWholeMilliseconds)
    storage.writeNextSet(oldChangeSet)

    storage.purge(1L, 1000L)

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("Only the flush removes change sets from the write buffer of a broken storage")
      .extracting<Long> { it.id }
      .containsExactly(oldChangeSet.id)
  }

  @Test
  fun `storage opens its files on the first use`() {
    storage.close(/*drop: */true)
    var openCount = 0
    storage = createStorage { path ->
      openCount++
      LocalHistoryStorage(path)
    }

    val changeSet = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(changeSet)
    assertThat(openCount)
      .describedAs("The storage must not open its files before the first use")
      .isZero()

    storage.flush()
    assertThat(openCount)
      .describedAs("The flush of a change set must open the storage")
      .isEqualTo(1)

    assertThat(storage.iterate().asSequence().toList())
      .extracting<Long> { it.id }
      .containsExactly(changeSet.id)
    assertThat(openCount)
      .describedAs("The storage must open its files only once")
      .isEqualTo(1)
  }

  @Test
  fun `close does not open the storage when the write buffer is empty`() {
    storage.close(/*drop: */true)
    var openCount = 0
    storage = createStorage { path ->
      openCount++
      LocalHistoryStorage(path)
    }

    storage.flush()
    storage.close(/*drop: */false)

    assertThat(openCount)
      .describedAs("The storage must not open its files when it has nothing to write")
      .isZero()
    storage = createStorage()
  }

  @Test
  fun `storage does not open its files after close`() {
    storage.close(/*drop: */true)
    var openCount = 0
    val closedStorage = createStorage { path ->
      openCount++
      LocalHistoryStorage(path)
    }
    closedStorage.close(/*drop: */false)

    closedStorage.writeNextSet(createChangeSet(closedStorage.nextId(), 1000L))
    closedStorage.flush()
    closedStorage.purge(1L, 1000L)
    closedStorage.iterate().forEach { _ -> }

    assertThat(openCount)
      .describedAs("A call after close must not open the storage")
      .isZero()
    storage = createStorage()
  }

  @Test
  fun `the first flush after the open purges the storage`() {
    storage.close(/*drop: */true)
    var openCount = 0
    storage = createStorage { path ->
      openCount++
      LocalHistoryStorage(path)
    }

    storage.flush()
    assertThat(openCount)
      .describedAs("The flush of an empty write buffer must not open the storage")
      .isZero()

    // 12 change sets that are 11 hours apart give more than the default 5 days of activity, so the purge removes the oldest one
    val changeSets = (0 until 12).map { createChangeSet(storage.nextId(), it * 11.hours.inWholeMilliseconds) }
    for (changeSet in changeSets) {
      storage.writeNextSet(changeSet)
    }
    storage.flush()

    assertThat(openCount)
      .describedAs("The flush of a change set must open the storage")
      .isEqualTo(1)
    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The first flush after the open must purge the obsolete change sets")
      .extracting<Long> { it.id }
      .containsExactlyElementsOf(changeSets.drop(1).reversed().map { it.id })
  }

  /** Replaces [storage] with a storage that cannot be opened, so it keeps the change sets in the write buffer. */
  private fun breakStorage() {
    storage.close(/*drop: */true)
    storage = createStorage { throw IOException("The test does not allow to open the storage") }
    // the storage opens its files on the first use
    storage.iterate().forEach { _ -> }
  }

  /** Returns the data file that contains the serialized change sets. */
  private fun storageDataPath(): Path = tempDir.resolve("storage/$STORAGE_FILE_NAME${AbstractStorage.DATA_EXTENSION}")

  /** Creates a recursive record chain that the storage must recover from. */
  private fun makeLastRecordPointToItself() {
    val context = StorageLockContext(false, true)
    val table = LocalHistoryRecordsTable(
      tempDir.resolve("storage/$STORAGE_FILE_NAME${AbstractStorage.INDEX_EXTENSION}"),
      context,
    )
    context.lockWrite()
    try {
      val lastRecord = table.lastRecord
      table.setPrevRecord(lastRecord, lastRecord)
      table.close()
    }
    finally {
      context.unlockWrite()
    }
  }

  private fun createChangeSet(id: Long, timestamp: Long, path: String = "file.txt"): ChangeSet {
    val cs = ChangeSet(id, timestamp)
    cs.addChange(CreateFileChange(id, path))
    cs.lock()
    return cs
  }

  private fun createContentChangeSet(id: Long, timestamp: Long, oldContent: Content): ChangeSet {
    val cs = ChangeSet(id, timestamp)
    cs.addChange(ContentChange(id, "file.txt", oldContent, timestamp))
    cs.lock()
    return cs
  }

  /** Counts the calls of [release]. Each call also runs [onRelease]. */
  private class ReleaseCountingContent(private val onRelease: () -> Unit = {}) : Content() {
    var releaseCount = 0

    override fun getBytes(): ByteArray = ByteArray(0)

    override fun isAvailable(): Boolean = true

    override fun release() {
      releaseCount++
      onRelease()
    }
  }

  private companion object {
    private const val STORAGE_FILE_NAME = "changes"

    /**
     * The current time in the tests.
     * The flush of a broken storage removes the change sets that are older than one hour before it.
     */
    private const val NOW = 10_000_000_000L
  }
}
