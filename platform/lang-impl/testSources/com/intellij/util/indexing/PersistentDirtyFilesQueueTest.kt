// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import it.unimi.dsi.fastutil.ints.IntList
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class PersistentDirtyFilesQueueTest {
  @get: Rule
  val tempDir = TempDirectory()

  @get: Rule
  val app = ApplicationRule()

  @Test
  fun `test store and load if vfs version`() {
    val file = tempDir.newDirectoryPath().resolve("test-PersistentDirtyFilesQueue")
    val queue = PersistentDirtyFilesQueue(file)
    val vfsVersion = 987L
    queue.storeIndexingQueue(IntList.of(1, 2, 3), vfsVersion)

    Assert.assertEquals(IntList.of(1, 2, 3), queue.readIndexingQueue(vfsVersion))
    Assert.assertEquals(IntList.of(), queue.readIndexingQueue(vfsVersion + 1))
  }
}