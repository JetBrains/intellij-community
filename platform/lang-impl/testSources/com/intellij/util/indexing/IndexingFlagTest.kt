// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.CacheSwitcher.switchIndexAndVfs
import com.intellij.idea.IJIgnore
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.dependencies.ReadWriteFileIndexingStampImpl
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class IndexingFlagTest {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @Rule
  @JvmField
  val temp = TempDirectory()

  @Rule
  @JvmField
  val edtRule = EdtRule()

  @Test
  @RunsInEdt
  fun testTumbler() {
    val fileBasedIndexTumbler = FileBasedIndexTumbler("IndexingFlagTest")
    val vFile = temp.newVirtualFile("test", "content".toByteArray())

    val fileIndexingStamp = ReadWriteFileIndexingStampImpl(1)
    IndexingFlag.setFileIndexed(vFile, fileIndexingStamp)

    try {
      fileBasedIndexTumbler.turnOff()
    }
    finally {
      fileBasedIndexTumbler.turnOn()
    }

    // should not throw exceptions, should be marked as indexed
    val indexed = IndexingFlag.isFileIndexed(vFile, fileIndexingStamp)
    assertTrue("Should be indexed because explicitly marked as indexed earlier", indexed)
    IndexingFlag.cleanProcessingFlag(vFile)
  }

  @Test
  @Ignore("Triggers `Path conflict. Existing symlink: SymlinkData{} vs. new symlink: SymlinkData{}`")
  @IJIgnore(issue = "IJPL-149673")
  @RunsInEdt
  fun indexingFlagIsKeptThroughVFSReload() {
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
    assertTrue("Should be indexed because explicitly marked as indexed earlier",
                 indexed)
    IndexingFlag.cleanProcessingFlag(vFileAfter)

    // should not throw exceptions
    IndexingStamp.getIndexStamp((vFileAfter as VirtualFileWithId).id, IdIndex.NAME)
  }
}