package com.intellij.util.indexing

import com.intellij.CacheSwitcher.switchIndexAndVfs
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsData
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import org.junit.Assert.assertEquals
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
    val fileBasedIndexTumbler = FileBasedIndexTumbler("test")
    val vFile = temp.newVirtualFile("test", "content".toByteArray())

    val fileIndexingStamp = ProjectIndexingDependenciesService.FileIndexingStampImpl(1)
    IndexingFlag.setFileIndexed(vFile, fileIndexingStamp)

    try {
      fileBasedIndexTumbler.turnOff()
    }
    finally {
      fileBasedIndexTumbler.turnOn()
    }

    // should not throw exceptions, should be marked as indexed
    val indexed = IndexingFlag.isFileIndexed(vFile, fileIndexingStamp)
    assertEquals("Should be indexed because explicitly marked as indexed earlier", !VfsData.isIsIndexedFlagDisabled(), indexed)
    IndexingFlag.cleanProcessingFlag(vFile)
  }

  @Test
  @RunsInEdt
  fun testCacheSwitcher() {
    val file = temp.newFile("test", "content".toByteArray())
    val vFileBefore = VfsUtil.findFileByIoFile(file, true) ?: throw AssertionError("File not found: $file")

    val fileIndexingStamp = ProjectIndexingDependenciesService.FileIndexingStampImpl(1)
    IndexingFlag.setFileIndexed(vFileBefore, fileIndexingStamp)

    switchIndexAndVfs(
      null,
      null,
      "resetting vfs"
    )

    val vFileAfter = VfsUtil.findFileByIoFile(file, true) ?: throw AssertionError("File not found: $file")

    // should not throw exceptions, should be marked as indexed
    val indexed = IndexingFlag.isFileIndexed(vFileAfter, fileIndexingStamp)
    assertEquals("Should be indexed because explicitly marked as indexed earlier", !VfsData.isIsIndexedFlagDisabled(), indexed)
    IndexingFlag.cleanProcessingFlag(vFileAfter)
  }
}