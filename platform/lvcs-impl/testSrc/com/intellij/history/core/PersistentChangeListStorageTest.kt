// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.CreateFileChange
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.impl.IndexDebugProperties
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.storage.AbstractStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE

@ParameterizedClass(name = "with cache: {0}")
@ValueSource(booleans = [true, false])
@TestApplication //needed for recovery since it wants notifications
internal class PersistentChangeListStorageTest(
  private val useCache: Boolean,
) {
  @TempDir
  private lateinit var tempDir: Path

  private lateinit var storage: PersistentChangeListStorage

  private fun createStorage() = PersistentChangeListStorage(
    storageDir = tempDir.resolve("storage"),
    getUseWriteCache = { useCache },
    getCurrentFsTimestamp = { 0 },
    unitTestMode = true
  )

  @BeforeEach
  fun setUp() {
    storage = createStorage()
  }

  @AfterEach
  fun tearDown() {
    storage.close(true)
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

    // data should be readable from the in-memory write cache even without flushing
    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(2)
    assertThat(result[0].id).isEqualTo(cs2.id)
    assertThat(result[1].id).isEqualTo(cs1.id)
  }

  @Test
  fun `flush moves data from write cache to persistent storage`() {
    val cs = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(cs)

    // before flush, data is in write cache; after reopen without flush it would be lost
    storage.flush()

    storage.close(false)
    storage = createStorage()

    val result = storage.iterate().asSequence().toList()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo(cs.id)
  }

  @Test
  fun `unflushed write cache data is flushed on close without drop`() {
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
  fun `lastId is preserved across multiple reopen cycles`() {
    val id1 = storage.nextId()
    storage.close(false)

    storage = createStorage()
    assertThat(storage.lastId).isEqualTo(id1)
    storage.nextId()
    storage.nextId()
    val id2 = storage.nextId()
    storage.close(false)

    storage = createStorage()
    assertThat(storage.lastId).isEqualTo(id2)
  }

  @Test
  fun `storage recovers when the data table was not closed`() {
    storage.writeNextSet(createChangeSet(storage.nextId(), 1000L))
    storage.flush()
    storage.close(/*dropStorages: */false)
    Files.write(storageDataPath(), ByteArray(Int.SIZE_BYTES), WRITE)

    val previousDebugValue = IndexDebugProperties.DEBUG
    IndexDebugProperties.DEBUG = true
    try {
      storage = createStorage()
    }
    finally {
      IndexDebugProperties.DEBUG = previousDebugValue
    }

    assertThat(storage.iterate().asSequence().toList())
      .describedAs("The recovered storage must not contain data from the damaged table")
      .isEmpty()
  }

  @Test
  fun `storage accepts writes after recovery from a recursive record chain`() {
    val originalChangeSet = createChangeSet(storage.nextId(), 1000L)
    storage.writeNextSet(originalChangeSet)
    storage.flush()
    storage.close(/*dropStorages: */false)
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

  private companion object {
    private const val STORAGE_FILE_NAME = "changes"
  }
}
