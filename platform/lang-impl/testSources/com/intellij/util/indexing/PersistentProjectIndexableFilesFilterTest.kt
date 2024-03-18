// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.projectFilter.ConcurrentFileIds
import com.intellij.util.io.DataOutputStream
import org.junit.Assert.assertEquals
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

  private fun doTest(size: Int) {
    val file = tempDir.newDirectoryPath().resolve("test-PersistentProjectIndexableFilesFilter")
    val ids = ConcurrentFileIds()
    for (i in 0 until size) {
      ids[i] = true
    }
    assertEquals(size, ids.cardinality)
    DataOutputStream(file.outputStream().buffered()).use {
      ids.writeTo(it)
    }
    val newIds = DataInputStream(file.inputStream().buffered()).use {
      ConcurrentFileIds.readFrom(it)
    }
    assertEquals(size, newIds.cardinality)
  }
}