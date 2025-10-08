// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.application
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService
import com.intellij.util.indexing.dependencies.IsFileChangedResult
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class IndexingFlagWithProjectTest {

  @JvmField
  @RegisterExtension
  val temp = TempDirectoryExtension()

  private val projectFixture = projectFixture(openAfterCreation = true)

  @Test
  fun indexingFlagIsClearedOnInvalidateAllStamps() = runBlocking {
    val project = projectFixture.get()
    val depService = project.service<ProjectIndexingDependenciesService>()
    var token = depService.newScanningTokenOnProjectOpen(true)

    val vFile = writeAction {
      val file = temp.newFile("test", "content".toByteArray())
      VfsUtil.findFileByIoFile(file, true) ?: throw AssertionError("File not found: $file")
    }

    var stamp = token.getFileIndexingStamp(vFile)
    IndexingFlag.setFileIndexed(vFile, stamp)

    var indexed = IndexingFlag.isFileIndexed(vFile, stamp)
    var isChanged = IndexingFlag.isFileChanged(vFile, stamp)
    assertTrue(indexed, "Should be indexed because explicitly marked as indexed earlier")
    assertEquals(IsFileChangedResult.NO, isChanged, "Should NOT be changed because explicitly marked as indexed earlier")

    val appDepService = application.service<AppIndexingDependenciesService>()
    appDepService.invalidateAllStamps("test")

    token = depService.newScanningTokenOnProjectOpen(true)
    stamp = token.getFileIndexingStamp(vFile)
    indexed = IndexingFlag.isFileIndexed(vFile, stamp)
    isChanged = IndexingFlag.isFileChanged(vFile, stamp)
    assertFalse(indexed, "Should NOT be indexed because invalidateAllStamps was invoked")
    assertEquals(IsFileChangedResult.YES, isChanged, "Should be changed because invalidateAllStamps was invoked")


    IndexingFlag.setFileIndexed(vFile, stamp)
    indexed = IndexingFlag.isFileIndexed(vFile, stamp)
    isChanged = IndexingFlag.isFileChanged(vFile, stamp)
    assertTrue(indexed, "Should be indexed because explicitly marked as indexed earlier")
    assertEquals(IsFileChangedResult.NO, isChanged, "Should NOT be changed because explicitly marked as indexed earlier")
  }
}