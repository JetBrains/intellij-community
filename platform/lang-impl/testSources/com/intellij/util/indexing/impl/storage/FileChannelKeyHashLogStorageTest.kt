// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.util.io.storages.CommonKeyDescriptors
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapIndexStorage
import com.intellij.util.indexing.impl.storage.durablemap.FileChannelKeyHashLogAccessor
import com.intellij.util.io.ChannelsAccessor
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation
import com.intellij.util.io.OpenChannelsCache
import com.intellij.util.io.PageCacheUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.EOFException
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class FileChannelKeyHashLogStorageTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `full and tail scans cross buffer boundaries and respect snapshots`() {
    val first = (0 until 10_000).map { (Int.MIN_VALUE + it) to (Int.MAX_VALUE - it) }
    val tail = (0 until 10_000).map { (Int.MAX_VALUE - it) to (Int.MIN_VALUE + it) }
    val base = directory.resolve("log")
    FileChannelKeyHashLogStorage(base).use { storage ->
      first.forEach { (first, second) -> storage.append(first, second) }
      val snapshot = storage.endPosition()
      tail.forEach { (first, second) -> storage.append(first, second) }
      assertEquals(first, storage.events(KeyHashLogStorage.Position.START, snapshot), "The scan must exclude the later tail")
      assertEquals(tail, storage.events(snapshot, storage.endPosition()), "The tail must include buffered and written records")
      assertEquals(emptyList<Pair<Int, Int>>(), storage.events(snapshot, snapshot))
    }
    FileChannelKeyHashLogStorage(base).use { storage ->
      assertEquals(first + tail, storage.events(), "Close must write the remaining events")
    }
  }

  @Test
  fun `flush writes exact file length without a length file`() {
    val base = directory.resolve("nested/log")
    FileChannelKeyHashLogStorage(base).use { storage ->
      storage.append(0, 0)
      storage.append(Int.MIN_VALUE, Int.MAX_VALUE)
      assertEquals(KeyHashLogStorage.Position(16), storage.endPosition())
      storage.flush()
      assertEquals(16L, Files.size(base.resolveSibling("log.project")), "The data file must contain no allocation padding")
      Files.newDirectoryStream(base.parent).use { files ->
        assertEquals(listOf("log.project"), files.map { it.fileName.toString() }, "The data file must contain its own length")
      }
    }
    FileChannelKeyHashLogStorage(base).use { storage ->
      storage.append(42, -1)
      assertEquals(listOf(0 to 0, Int.MIN_VALUE to Int.MAX_VALUE, 42 to -1), storage.events())
    }
  }

  @Test
  fun `relative and absolute paths identify the same data file`() {
    val absoluteBase = directory.resolve("log")
    val relativeBase = Path.of("").toAbsolutePath().relativize(absoluteBase)
    FileChannelKeyHashLogStorage(relativeBase).use { storage ->
      storage.append(42, 1)
    }
    FileChannelKeyHashLogStorage(absoluteBase).use { storage ->
      assertEquals(listOf(42 to 1), storage.events(), "Both paths must identify the same data file")
    }
  }

  @Test
  fun `clear removes written and buffered events and the compaction marker`() {
    val base = directory.resolve("log")
    FileChannelKeyHashLogStorage(base).use { storage ->
      storage.append(1, 1)
      storage.flush()
      storage.append(2, 2)
      storage.setCompactionRequired()
      storage.setCompactionRequired()
      assertTrue(storage.isCompactionRequired)
      storage.clear()
      assertEquals(KeyHashLogStorage.Position.START, storage.endPosition())
      assertEquals(0L, Files.size(directory.resolve("log.project")))
      assertFalse(storage.isCompactionRequired)
      storage.append(3, 3)
    }
    FileChannelKeyHashLogStorage(base).use { storage ->
      assertEquals(listOf(3 to 3), storage.events(), "Cleared records must remain absent after reopening")
      assertFalse(storage.isCompactionRequired)
    }
  }

  @Test
  fun `scan can restart after an early stop or processor failure`() {
    FileChannelKeyHashLogStorage(directory.resolve("log")).use { storage ->
      repeat(10_000) { storage.append(it, it + 1) }
      var calls = 0
      assertFalse(storage.scan(KeyHashLogStorage.Position.START, storage.endPosition()) { _, _ -> ++calls < 8193 })
      assertEquals(8193, calls, "The processor must stop the scan across a block boundary")
      assertThrows(IOException::class.java) {
        storage.scan(KeyHashLogStorage.Position.START, storage.endPosition()) { _, _ -> throw IOException("Processor failure") }
      }
      assertEquals(10_000, storage.events().size, "A failed processor must leave the log available")
    }
  }

  @Test
  fun `positions reject unaligned negative and out of range values`() {
    FileChannelKeyHashLogStorage(directory.resolve("log")).use { storage ->
      storage.append(1, 1)
      val end = storage.endPosition()
      for (invalid in listOf(-8L, 1L, 7L, 9L, 16L, Long.MAX_VALUE)) {
        val position = KeyHashLogStorage.Position(invalid)
        assertFalse(storage.isPositionInRange(position, end), "Invalid position: $invalid")
        assertThrows(IOException::class.java) { storage.events(position, end) }
        assertThrows(IOException::class.java) { storage.events(KeyHashLogStorage.Position.START, position) }
      }
      assertTrue(storage.isPositionInRange(KeyHashLogStorage.Position.START, end))
      assertTrue(storage.isPositionInRange(end, end))
    }
  }

  @Test
  fun `opening a truncated record fails and releases the channel`() {
    val base = directory.resolve("log")
    val dataFile = directory.resolve("log.project")
    Files.write(dataFile, ByteArray(9))
    assertThrows(EOFException::class.java) { FileChannelKeyHashLogStorage(base) }
    Files.write(dataFile, ByteArray(8))
    FileChannelKeyHashLogStorage(base).use { storage ->
      assertEquals(listOf(0 to 0), storage.events(), "A repaired file must open through the same cache")
    }
  }

  @Test
  fun `cache reuses a channel and reopens it after eviction`() {
    var opens = 0
    val cache = OpenChannelsCache("key hash log test", 1) { path, readOnly ->
      opens++
      PageCacheUtils.RESILIENT_CHANNEL_OPENER.open(path, readOnly)
    }
    val accessor = cache.asWritable()
    val base = directory.resolve("log")
    FileChannelKeyHashLogStorage(base, accessor).use { storage ->
      storage.append(1, 1)
      storage.flush()
      assertEquals(listOf(1 to 1), storage.events())
      assertEquals(1, opens, "The constructor, flush, and scan must reuse one channel")
      FileChannelKeyHashLogStorage(directory.resolve("other"), accessor).use { other ->
        other.append(2, 2)
        other.flush()
        assertEquals(listOf(1 to 1), storage.events(), "Eviction must preserve the log contents")
      }
      assertEquals(3, opens, "The evicted channel must reopen for the scan")
    }
    FileChannelKeyHashLogStorage(base, accessor).use { storage ->
      assertEquals(listOf(1 to 1), storage.events())
      assertEquals(4, opens, "Closing a storage must release its cached channel")
    }
    assertThrows(IllegalArgumentException::class.java) {
      FileChannelKeyHashLogStorage(directory.resolve("readonly"), cache.asReadOnly())
    }
  }

  @Test
  fun `repeated channel operations do not duplicate records or processor calls`() {
    val delegate = PageCacheUtils.getCachedChannelsAccessor(false)
    val accessor = object : ChannelsAccessor by delegate {
      override fun <T> executeIdempotentOp(path: Path, operation: FileChannelIdempotentOperation<T?>): T? {
        delegate.executeIdempotentOp(path, operation)
        return delegate.executeIdempotentOp(path, operation)
      }
    }
    FileChannelKeyHashLogStorage(directory.resolve("log"), accessor).use { storage ->
      val events = (0 until 10_000).map { it to -(it + 1) }
      events.forEach { (first, second) -> storage.append(first, second) }
      assertEquals(events, storage.events(), "A repeated read must deliver each event once")
      assertEquals(80_000L, Files.size(directory.resolve("log.project")), "A repeated write must use the same offsets")
    }
  }

  @Test
  fun `failed flush retains the buffer for a retry`() {
    val delegate = PageCacheUtils.getCachedChannelsAccessor(false)
    var fail = true
    val accessor = object : ChannelsAccessor by delegate {
      override fun <T> executeIdempotentOp(path: Path, operation: FileChannelIdempotentOperation<T?>): T? {
        val result = delegate.executeIdempotentOp(path, operation)
        if (fail) throw IOException("Failure after writing")
        return result
      }
    }
    FileChannelKeyHashLogStorage(directory.resolve("log"), accessor).use { storage ->
      storage.append(1, 1)
      assertThrows(IOException::class.java) { storage.flush() }
      assertEquals(KeyHashLogStorage.Position(8), storage.endPosition(), "A failed flush must preserve the logical end")
      fail = false
      storage.append(2, 2)
      storage.flush()
      assertEquals(listOf(1 to 1, 2 to 2), storage.events(), "The retry must retain all buffered records exactly once")
      assertEquals(16L, Files.size(directory.resolve("log.project")))
    }
  }

  @Test
  fun `closed storage rejects further channel operations`() {
    val storage = FileChannelKeyHashLogStorage(directory.resolve("log"))
    storage.append(1, 1)
    storage.close()
    storage.close()
    assertThrows(ClosedChannelException::class.java) { storage.append(2, 2) }
    assertThrows(ClosedChannelException::class.java) { storage.endPosition() }
    assertThrows(ClosedChannelException::class.java) { storage.events() }
    assertThrows(ClosedChannelException::class.java) { storage.clear() }
    assertThrows(ClosedChannelException::class.java) { storage.flush() }
  }

  @Test
  fun `key hash log restores checkpoints and compacts the alternative storage`() {
    val base = directory.resolve("log")
    val checkpoints = FileKeyHashLogCheckpointStore("checkpoint", directory)
    val project = ProjectManager.getInstance().defaultProject
    val filter = object : IdFilter() {
      override fun containsFileId(id: Int): Boolean = id == 1
      override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES
    }
    FileChannelKeyHashLogStorage(base).let { storage ->
      KeyHashLog(EnumeratorStringDescriptor.INSTANCE, storage, checkpoints).use { log ->
        log.addKeyHashToVirtualFileMapping("initial", 1)
        assertTrue(log.getSuitableKeyHashes(filter, project).test("initial".hashCode()))
        log.addKeyHashToVirtualFileMapping("later", 1)
        assertTrue(log.getSuitableKeyHashes(filter, project).test("later".hashCode()))
        assertEquals(storage.endPosition(), checkpoints.load(project.hashCode()).scannedUpTo())
      }
    }
    val scans = ArrayList<KeyHashLogStorage.Position>()
    val reopened = FileChannelKeyHashLogStorage(base)
    val recording = object : KeyHashLogStorage by reopened {
      override fun scan(from: KeyHashLogStorage.Position, to: KeyHashLogStorage.Position, processor: KeyHashLogStorage.Processor): Boolean {
        scans.add(from)
        return reopened.scan(from, to, processor)
      }
    }
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, recording, checkpoints).use { log ->
      log.addKeyHashToVirtualFileMapping("last", 1)
      assertTrue(log.getSuitableKeyHashes(filter, project).test("last".hashCode()))
      assertEquals(listOf(KeyHashLogStorage.Position(16)), scans, "The restored checkpoint must scan only the tail")
      log.removeKeyHashToVirtualFileMapping("initial", 1)
      log.addKeyHashToVirtualFileMapping("later", 1)
      reopened.setCompactionRequired()
    }
    FileChannelKeyHashLogStorage(base).let { storage ->
      KeyHashLog(EnumeratorStringDescriptor.INSTANCE, storage, checkpoints).use { log ->
        assertFalse(storage.isCompactionRequired, "Opening the log must consume the compaction marker")
        assertEquals(setOf("later".hashCode() to 1, "last".hashCode() to 1), storage.events().toSet())
        assertEquals(KeyHashLogStorage.Position(16), storage.endPosition(), "Compaction must remove duplicates and removals")
        val hashes = log.getSuitableKeyHashes(filter)
        assertFalse(hashes.contains("initial".hashCode()))
        assertTrue(hashes.contains("later".hashCode()))
      }
    }
  }

  @Test
  fun `index storage includes the journal tail in cached project queries`() {
    val project = ProjectManager.getInstance().defaultProject
    val scope = GlobalSearchScope.allScope(project)
    val filter = object : IdFilter() {
      override fun containsFileId(id: Int): Boolean = id == 1
      override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES
    }
    val file = directory.resolve("index")
    val keyDescriptor = CommonKeyDescriptors.stringAsUTF8()
    DurableMapIndexStorage(
      file, keyDescriptor, EnumeratorStringDescriptor.INSTANCE, 16, false, true,
      FileChannelKeyHashLogAccessor(FileChannelKeyHashLogAccessor.baseFileFor(DurableMapIndexStorage.getIndexStorageFile(file)), keyDescriptor)
    ).use { storage ->
      storage.addValue("initial", 1, "first")
      val keys = mutableSetOf<String>()
      storage.processKeys({ keys.add(it); true }, scope, filter)
      assertEquals(setOf("initial"), keys)
      storage.addValue("tail", 1, "second")
      keys.clear()
      storage.processKeys({ keys.add(it); true }, scope, filter)
      assertEquals(setOf("initial", "tail"), keys, "A cached query must include new journal events")
    }
  }

  private fun KeyHashLogStorage.events(
    from: KeyHashLogStorage.Position = KeyHashLogStorage.Position.START,
    to: KeyHashLogStorage.Position = endPosition(),
  ): List<Pair<Int, Int>> {
    val result = ArrayList<Pair<Int, Int>>()
    assertTrue(scan(from, to) { hash, id -> result.add(hash to id); true }, "The scan must complete")
    return result
  }
}
