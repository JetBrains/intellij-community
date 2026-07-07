// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.indexing.events.VfsEventsMerger.VfsEventProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.assertThrows

class VfsEventsMergerTest {
  /** Ensures cancellation during preparation does not discard a change that must be processed later. */
  @Test
  fun changeRemainsQueuedWhenPreparationIsCancelled() {
    val merger = VfsEventsMerger()
    merger.recordFileEvent(TestVirtualFile("test.txt"), true)

    assertThrows<ProcessCanceledException> {
      merger.processChanges(object : VfsEventProcessor {
        override fun prepare(changeInfo: VfsEventsMerger.ChangeInfo) {
          throw ProcessCanceledException()
        }

        override fun process(changeInfo: VfsEventsMerger.ChangeInfo): Boolean {
          fail("The cancelled change must not be processed")
        }
      })
    }
    assertTrue(merger.hasChanges())

    var processed = false
    merger.processChanges {
      processed = true
      true
    }

    assertTrue(processed)
    assertFalse(merger.hasChanges())
  }

  private class TestVirtualFile(name: String) : LightVirtualFile(name), VirtualFileWithId {
    override fun getId(): Int = 1
  }
}
