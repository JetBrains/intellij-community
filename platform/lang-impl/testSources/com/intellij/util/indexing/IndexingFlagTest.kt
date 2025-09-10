// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.CacheSwitcher.switchIndexAndVfs
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.indexing.dependencies.ReadWriteFileIndexingStampImpl
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
class IndexingFlagTest {

  @JvmField
  @RegisterExtension
  val temp = TempDirectoryExtension()

  @Test
  fun testTumbler() {
    val fileBasedIndexTumbler = FileBasedIndexTumbler("IndexingFlagTest")
    WriteIntentReadAction.compute(Computable {
      val vFile = temp.newVirtualFile("test", "content".toByteArray())


      val fileIndexingStamp = ReadWriteFileIndexingStampImpl(1)
      IndexingFlag.setFileIndexed(vFile, fileIndexingStamp)

      try {
        fileBasedIndexTumbler.turnOff()
      }
      finally {
        fileBasedIndexTumbler.turnOn()
        // should not throw exceptions, should be marked as indexed
        val indexed = IndexingFlag.isFileIndexed(vFile, fileIndexingStamp)
        assertTrue(indexed, "Should be indexed because explicitly marked as indexed earlier")
        IndexingFlag.cleanProcessingFlag(vFile)
      }
    })
  }

  @Test
  fun indexingFlagIsKeptThroughVFSReload() {
    WriteIntentReadAction.compute(Computable {
      val file = temp.newFile("test", "content".toByteArray())
      val vFileBefore = VfsUtil.findFileByIoFile(file, true) ?: throw AssertionError("File not found: $file")

      val fileIndexingStamp = ReadWriteFileIndexingStampImpl(1)
      IndexingFlag.setFileIndexed(vFileBefore, fileIndexingStamp)

      switchIndexAndVfs(
        null,
        null,
        "resetting vfs"
      )

      val vFileAfter = VfsUtil.findFileByIoFile(file, true) ?: throw AssertionError("File not found: $file")

      // should not throw exceptions, should be marked as indexed
      val indexed = IndexingFlag.isFileIndexed(vFileAfter, fileIndexingStamp)
      assertTrue(indexed, "Should be indexed because explicitly marked as indexed earlier")
      IndexingFlag.cleanProcessingFlag(vFileAfter)

      // should not throw exceptions
      IndexingStamp.getIndexStamp((vFileAfter as VirtualFileWithId).id, IdIndex.NAME)
    })
  }
}