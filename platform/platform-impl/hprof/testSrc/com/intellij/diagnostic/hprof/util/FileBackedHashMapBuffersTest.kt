// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal class FileBackedHashMapBuffersTest {

  private lateinit var channel: FileChannel

  @BeforeEach
  fun setUp() {
    // A mapped buffer keeps the file open on Windows, so the file deletes itself after the last unmap.
    channel = FileChannel.open(Files.createTempFile("hprof-map-", ".tmp"),
                               StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE)
  }

  @AfterEach
  fun tearDown() {
    channel.close()
  }

  // 30 entries give 39 buckets. A shift of 2 puts 4 buckets in each buffer, so the map has 10 buffers.
  private fun createMap(): FileBackedHashMap = FileBackedHashMap.createEmpty(channel, 30, 8, 4, 2)

  @Test
  fun `stores keys in every buffer`() {
    val map = createMap()
    for (key in 1L..30L) {
      map.put(key).putInt(key.toInt() * 10)
    }
    for (key in 1L..30L) {
      assertEquals(key.toInt() * 10, map[key]!!.int)
    }
    assertNull(map[31])
  }

  @Test
  fun `probes from the last bucket of a buffer into the next buffer`() {
    val map = createMap()
    // The keys 3, 42 and 81 all hash to bucket 3, the last bucket of the first buffer.
    map.put(3).putInt(30)
    map.put(42).putInt(420)
    map.put(81).putInt(810)

    assertEquals(30, map[3]!!.int)
    assertEquals(420, map[42]!!.int)
    assertEquals(810, map[81]!!.int)
    assertTrue(map.containsKey(81))
    // The key 120 also hashes to bucket 3. Its probe stops at the empty bucket 6.
    assertFalse(map.containsKey(120))
  }

  @Test
  fun `wraps from the last bucket of the map to the first bucket`() {
    val map = createMap()
    // The keys 38, 77 and 116 all hash to bucket 38, the last bucket of the map.
    map.put(38).putInt(380)
    map.put(77).putInt(770)
    map.put(116).putInt(1160)
    map.put(77).putInt(771)

    assertEquals(380, map[38]!!.int)
    assertEquals(771, map[77]!!.int)
    assertEquals(1160, map[116]!!.int)
    assertNull(map[155])
  }

  @Test
  fun `maps overlapping buffers`() {
    channel.write(ByteBuffer.wrap(ByteArray(10) { it.toByte() }))

    val buffers = FileBackedHashMap.mapBuffers(channel, FileChannel.MapMode.READ_ONLY, 4, 6)

    assertEquals(listOf(6, 6, 2), buffers.map { it.capacity() })
    assertEquals(listOf<Byte>(0, 4, 8), buffers.map { it.get(0) })
    assertEquals(9.toByte(), buffers[1].get(5))
  }

  @Test
  fun `maps each overlapping buffer of a file above the single map limit`() {
    channel.write(ByteBuffer.wrap(ByteArray(10) { it.toByte() }))

    val buffers = FileBackedHashMap.mapBuffers(channel, FileChannel.MapMode.READ_ONLY, 4, 6, singleMapLimit = 9)

    assertEquals(listOf(6, 6, 2), buffers.map { it.capacity() })
    assertEquals(listOf<Byte>(0, 4, 8), buffers.map { it.get(0) })
    assertEquals(9.toByte(), buffers[1].get(5))
  }
}
