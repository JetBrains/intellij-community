// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.projectFilter.ConcurrentFileIds
import com.intellij.util.indexing.projectFilter.readIndexableFilesFilter
import com.intellij.util.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.DataInputStream
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class PersistentProjectIndexableFilesFilterTest {
  @get: Rule
  val tempDir: TempDirectory = TempDirectory()

  @Test
  fun `test file ids store and read`() {
    doTest(1000 * Integer.SIZE)
  }

  @Test
  fun `test file ids store and read 2`() {
    doTest(1000 * Integer.SIZE + 1)
  }

  @Test
  fun `test reading filter of version 1`() {
    val file = tempDir.newDirectoryPath().resolve("test-PersistentProjectIndexableFilesFilter")
    DataOutputStream(file.outputStream().buffered()).use {
      it.writeInt(1) // version
      createFileIds(10).writeTo(it)
    }
    val filter = readIndexableFilesFilter(file, currentVfsCreationTimestamp = 1)
    assertFalse(filter.wasDataLoadedFromDisk) // filter should be discarded because vfsCreationTimestamp might have changed
  }

  private fun doTest(size: Int) {
    val file = tempDir.newDirectoryPath().resolve("test-PersistentProjectIndexableFilesFilter")
    val ids = createFileIds(size)
    assertEquals(size, ids.cardinality)
    DataOutputStream(file.outputStream().buffered()).use {
      ids.writeTo(it)
    }
    val newIds = DataInputStream(file.inputStream().buffered()).use {
      ConcurrentFileIds.readFrom(it)
    }
    assertEquals(size, newIds.cardinality)
  }

  private fun createFileIds(size: Int): ConcurrentFileIds {
    val ids = ConcurrentFileIds()
    for (i in 0 until size) {
      ids[i] = true
    }
    return ids
  }
}