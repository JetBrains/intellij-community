// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal class FileBackedObjectMetaListTest {

  private lateinit var channel: FileChannel

  @BeforeEach
  fun setUp() {
    // A mapped buffer keeps the file open on Windows, so the file deletes itself after the last unmap.
    channel = FileChannel.open(Files.createTempFile("hprof-meta-", ".tmp"),
                               StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE)
  }

  @AfterEach
  fun tearDown() {
    channel.close()
  }

  // A shift of 2 puts 4 records in each buffer, so 10 records use 3 buffers.
  private fun createList(): FileBackedObjectMetaList = FileBackedObjectMetaList.createEmpty(channel, 10, 2)

  @Test
  fun `keeps the fields of each record in every buffer`() {
    val list = createList()
    for (index in 0 until 10) {
      list.parentList[index] = index + 100
      list.sizesList[index] = index + 200
      list.refIndexList[index] = index + 10
      list.classIndexList[index] = index + 300
    }
    for (index in 0 until 10) {
      assertEquals(index + 100, list.parentList[index])
      assertEquals(index + 200, list.sizesList[index])
      assertEquals(index + 10, list.refIndexList[index])
      assertEquals(index + 300, list.classIndexList[index])
    }
  }

  @Test
  fun `keeps the packed fields apart at a buffer boundary`() {
    val list = createList()
    // The indexes 3 and 4 are the last record of the first buffer and the first record of the second buffer.
    list.classIndexList[3] = 0xFFFFFF
    list.refIndexList[3] = 255
    list.refIndexList[4] = 1
    list.classIndexList[4] = 1
    list.refIndexList[3] = 7

    assertEquals(0xFFFFFF, list.classIndexList[3])
    assertEquals(7, list.refIndexList[3])
    assertEquals(1, list.classIndexList[4])
    assertEquals(1, list.refIndexList[4])
  }

  @Test
  fun `stores a class index above the packed capacity as zero`() {
    val list = createList()
    list.refIndexList[9] = 5
    list.classIndexList[9] = 0x1000000

    assertEquals(0, list.classIndexList[9])
    assertEquals(5, list.refIndexList[9])
  }

  @Test
  fun `keeps all 32 bits of the subtree size`() {
    val list = createList()
    list.sizesList[8] = 0xFFFF_FFFF.toInt()

    assertEquals(0xFFFF_FFFFL, list.sizesList[8].toUInt().toLong())
  }
}
