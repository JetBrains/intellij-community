// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.StorageException
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class KeyHashLogStorageTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `full scan and tail scan stop at the supplied snapshot`() {
    FileKeyHashLogStorage(directory.resolve("log"), null).use { storage ->
      val first = (0 until 1000).map { (Int.MIN_VALUE + it) to it + 1 }
      val tail = (0 until 1000).map { (Int.MAX_VALUE - it) to -(it + 1) }
      first.forEach { (first, second) -> storage.append(first, second) }
      val prefix = storage.endPosition()
      tail.forEach { (first, second) -> storage.append(first, second) }
      val end = storage.endPosition()

      assertEquals(first, storage.events(KeyHashLogStorage.Position.START, prefix), "A full scan must exclude the later tail")
      assertEquals(tail, storage.events(prefix, end), "A tail scan must include records across append buffers")
      assertEquals(emptyList<Pair<Int, Int>>(), storage.events(end, end), "An empty range must not deliver an event")
      assertFalse(storage.isPositionInRange(end, prefix), "A position beyond the snapshot must be rejected")
    }
  }

  @Test
  fun `clear removes stored and buffered events and permits new appends`() {
    val base = directory.resolve("log")
    FileKeyHashLogStorage(base, null).use { storage ->
      storage.append(1, 1)
      storage.flush()
      storage.append(2, 2)
      storage.setCompactionRequired()

      storage.clear()

      assertEquals(KeyHashLogStorage.Position.START, storage.endPosition(), "A cleared log must have no events")
      assertFalse(storage.isCompactionRequired, "A cleared log must not require compaction")
      storage.append(3, 3)
      assertEquals(listOf(3 to 3), storage.events(KeyHashLogStorage.Position.START, storage.endPosition()),
                   "New appends must not expose the cleared events")
    }
    FileKeyHashLogStorage(base, null).use { storage ->
      assertEquals(listOf(3 to 3), storage.events(KeyHashLogStorage.Position.START, storage.endPosition()),
                   "Cleared events must stay absent after reopening")
    }
  }

  @Test
  fun `scan supports early stop and retry`() {
    FileKeyHashLogStorage(directory.resolve("log"), null).use { storage ->
      storage.append(1, 1)
      val prefix = storage.endPosition()
      storage.append(2, 2)
      storage.append(3, 3)
      val end = storage.endPosition()
      for (start in listOf(KeyHashLogStorage.Position.START, prefix)) {
        var visited = 0
        assertFalse(storage.scan(start, end) { _, _ -> visited++; false }, "The scan must report a processor stop")
        assertEquals(1, visited, "The scan must stop after the first callback")
      }
      assertThrows(IOException::class.java) {
        storage.scan(prefix, end) { _, _ -> throw IOException("Read failed") }
      }
      assertEquals(listOf(2 to 2, 3 to 3), storage.events(prefix, end), "An exception must release the tail read lock")
    }
  }

  @Test
  fun `file storage reads the previous event format`() {
    val base = directory.resolve("log")
    val externalizer = object : DataExternalizer<IntArray> {
      override fun save(out: DataOutput, value: IntArray) {
        DataInputOutputUtil.writeINT(out, value[0])
        DataInputOutputUtil.writeINT(out, value[1])
      }

      override fun read(input: DataInput): IntArray = error("The fixture only writes records")
    }
    val mapping = AppendableStorageBackedByResizableMappedFile(
      directory.resolve("log.project"), 4096, null, IOUtil.MiB, true, externalizer
    )
    mapping.lockWrite()
    try {
      mapping.use {
        it.append(intArrayOf("Aa".hashCode(), 1))
        it.append(intArrayOf("BB".hashCode(), 2))
        it.append(intArrayOf("Aa".hashCode(), -1))
      }
    }
    finally {
      mapping.unlockWrite()
    }

    KeyHashLog(
      EnumeratorStringDescriptor.INSTANCE,
      FileKeyHashLogStorage(base, null),
      FileKeyHashLogCheckpointStore(base),
    ).use { log ->
      assertTrue(log.getSuitableKeyHashes(filter(2)).contains("BB".hashCode()), "Another file must retain a colliding hash")
      assertFalse(log.getSuitableKeyHashes(filter(1)).contains("Aa".hashCode()), "The removed file must not retain the hash")
    }
  }

  @Test
  fun `file checkpoint preserves format version two`() {
    FileKeyHashLogStorage(directory.resolve("log"), null).use { storage ->
      storage.append(42, 1)
      val end = storage.endPosition()
      val store = FileKeyHashLogCheckpointStore("checkpoint", directory)
      store.save(7, KeyHashLogCheckpointStore.Checkpoint(end, 11, 1, IntOpenHashSet(intArrayOf(42))))
      val path = Files.newDirectoryStream(directory, "checkpoint.*").use { it.single() }
      DataInputStream(Files.newInputStream(path)).use { input ->
        assertEquals(2, DataInputOutputUtil.readINT(input), "The checkpoint version must remain unchanged")
        assertEquals(2, DataInputOutputUtil.readLONG(input), "The position must retain the previous byte offset encoding")
        assertEquals(11, DataInputOutputUtil.readLONG(input), "The modification count must retain its field order")
        assertEquals(1, DataInputOutputUtil.readINT(input))
        assertEquals(1, DataInputOutputUtil.readINT(input))
        assertEquals(42, DataInputOutputUtil.readINT(input))
        assertEquals(-1, input.read(), "The checkpoint must not contain extra fields")
      }

      // Write the old layout independently of the new checkpoint writer.
      DataOutputStream(Files.newOutputStream(path)).use { output ->
        DataInputOutputUtil.writeINT(output, 2)
        DataInputOutputUtil.writeLONG(output, 2)
        DataInputOutputUtil.writeLONG(output, 13)
        DataInputOutputUtil.writeINT(output, 1)
        DataInputOutputUtil.writeINT(output, 1)
        DataInputOutputUtil.writeINT(output, 99)
      }
      val restored = store.load(7)
      assertEquals(end, restored.scannedUpTo(), "The old offset must restore an opaque position")
      assertEquals(13, restored.modificationCount())
      assertEquals(1, restored.removesUnaccountedFor())
      assertEquals(setOf(99), restored.keyHashes())

      Files.write(path, byteArrayOf(2))
      assertThrows(IOException::class.java) { store.load(7) }
    }
  }

  @Test
  fun `cache uses one snapshot for a full replay and a tail replay`() {
    assumeTrue(KeyHashLog.ENABLE_CACHED_HASH_IDS)
    val fileStorage = FileKeyHashLogStorage(directory.resolve("log"), null)
    val storage = object : KeyHashLogStorage by fileStorage {
      var appendOnSnapshot: String? = null

      override fun endPosition(): KeyHashLogStorage.Position {
        val snapshot = fileStorage.endPosition()
        appendOnSnapshot?.let {
          appendOnSnapshot = null
          fileStorage.append(it.hashCode(), 1)
        }
        return snapshot
      }
    }
    val checkpointStore = FileKeyHashLogCheckpointStore("checkpoint", directory)
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, storage, checkpointStore).use { log ->
      log.addKeyHashToVirtualFileMapping("initial", 1)
      storage.appendOnSnapshot = "later"
      val project = ProjectManager.getInstance().defaultProject
      val initial = log.getSuitableKeyHashes(filter(1), project)
      assertTrue(initial.test("initial".hashCode()))
      assertFalse(initial.test("later".hashCode()), "A full replay must use the position captured before the append")

      storage.appendOnSnapshot = "last"
      val updated = log.getSuitableKeyHashes(filter(1), project)
      assertTrue(updated.test("later".hashCode()))
      assertFalse(updated.test("last".hashCode()), "A tail replay must use the position captured before the append")
      assertTrue(log.getSuitableKeyHashes(filter(1), project).test("last".hashCode()), "The next replay must read the remaining tail")
    }
  }

  @Test
  fun `cache retries the tail after an exception`() {
    checkTailRetry(throwException = true)
  }

  @Test
  fun `cache retries the tail after an early stop`() {
    checkTailRetry(throwException = false)
  }

  private fun checkTailRetry(throwException: Boolean) {
    assumeTrue(KeyHashLog.ENABLE_CACHED_HASH_IDS)
    val fileStorage = FileKeyHashLogStorage(directory.resolve("log"), null)
    val storage = object : KeyHashLogStorage by fileStorage {
      var fail = false

      override fun scan(
        from: KeyHashLogStorage.Position,
        to: KeyHashLogStorage.Position,
        processor: KeyHashLogStorage.Processor,
      ): Boolean {
        return fileStorage.scan(from, to) { hash, inputId ->
          val proceed = processor.process(hash, inputId)
          if (fail) {
            if (throwException) throw IOException("Read failed")
            false
          }
          else proceed
        }
      }
    }
    val checkpointStore = FileKeyHashLogCheckpointStore("checkpoint", directory)
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, storage, checkpointStore).use { log ->
      val project = ProjectManager.getInstance().defaultProject
      log.addKeyHashToVirtualFileMapping("old", 1)
      log.getSuitableKeyHashes(filter(1), project)
      val before = checkpointStore.load(project.hashCode()).scannedUpTo()
      log.removeKeyHashToVirtualFileMapping("old", 1)
      log.addKeyHashToVirtualFileMapping("new", 1)

      storage.fail = true
      assertThrows(StorageException::class.java) { log.getSuitableKeyHashes(filter(1), project) }
      assertEquals(before, checkpointStore.load(project.hashCode()).scannedUpTo(), "An incomplete scan must not publish its position")
      storage.fail = false
      assertTrue(log.getSuitableKeyHashes(filter(1), project).test("new".hashCode()), "The retry must include the unprocessed addition")
      assertEquals(1, checkpointStore.load(project.hashCode()).removesUnaccountedFor(), "The retry must count the removal once")
      assertEquals(storage.endPosition(), checkpointStore.load(project.hashCode()).scannedUpTo())
    }
  }

  @Test
  fun `removal threshold triggers an exact replay`() {
    assumeTrue(KeyHashLog.ENABLE_CACHED_HASH_IDS)
    val base = directory.resolve("log")
    KeyHashLog(
      EnumeratorStringDescriptor.INSTANCE,
      FileKeyHashLogStorage(base, null),
      FileKeyHashLogCheckpointStore(base),
    ).use { log ->
      val project = ProjectManager.getInstance().defaultProject
      log.addKeyHashToVirtualFileMapping("old", 1)
      log.addKeyHashToVirtualFileMapping("keep", 1)
      log.getSuitableKeyHashes(filter(1), project)
      repeat(65) { log.removeKeyHashToVirtualFileMapping("old", 1) }
      assertTrue(log.getSuitableKeyHashes(filter(1), project).test("old".hashCode()), "A tail scan can retain a removed hash")
      val rebuilt = log.getSuitableKeyHashes(filter(1), project)
      assertFalse(rebuilt.test("old".hashCode()), "The next query must rebuild after the removal threshold")
      assertTrue(rebuilt.test("keep".hashCode()), "The full replay must retain active hashes")
    }
  }

  @Test
  fun `checkpoint failures permit a full replay and a tail replay`() {
    assumeTrue(KeyHashLog.ENABLE_CACHED_HASH_IDS)
    val storage = FileKeyHashLogStorage(directory.resolve("log"), null)
    val checkpoints = object : KeyHashLogCheckpointStore {
      override fun load(checkpointId: Int): KeyHashLogCheckpointStore.Checkpoint = throw IOException("Checkpoint unavailable")
      override fun save(checkpointId: Int, checkpoint: KeyHashLogCheckpointStore.Checkpoint): Unit = throw IOException("Checkpoint unavailable")
    }
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, storage, checkpoints).use { log ->
      val project = ProjectManager.getInstance().defaultProject
      log.addKeyHashToVirtualFileMapping("initial", 1)
      assertTrue(log.getSuitableKeyHashes(filter(1), project).test("initial".hashCode()), "A missing checkpoint must permit a full replay")
      log.addKeyHashToVirtualFileMapping("later", 1)
      assertTrue(log.getSuitableKeyHashes(filter(1), project).test("later".hashCode()), "A failed save must permit a tail replay")
    }
  }

  private fun KeyHashLogStorage.events(from: KeyHashLogStorage.Position, to: KeyHashLogStorage.Position): List<Pair<Int, Int>> {
    val events = ArrayList<Pair<Int, Int>>()
    assertTrue(scan(from, to) { hash, id -> events.add(hash to id); true }, "The scan must complete")
    return events
  }

  private fun filter(vararg ids: Int): IdFilter = object : IdFilter() {
    override fun containsFileId(id: Int): Boolean = id in ids
    override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES
  }
}
