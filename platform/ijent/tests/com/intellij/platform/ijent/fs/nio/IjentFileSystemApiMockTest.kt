// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs.nio

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFsResult
import com.intellij.platform.ijent.fs.nio.FsKind.UNIX
import io.kotest.matchers.collections.shouldBeEmpty
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Suppress("ClassName")
class IjentFileSystemApiMockTest {
  @Nested
  inner class listDirectory {
    @Test
    @Disabled("Not implemented yet")  // TODO Implement.
    fun `empty directory`() = runTest {
      val fsApi = mockIjentFileSystemApi(UNIX)
      fsApi.fileTree.modify {
        root("/") {}
      }

      val result = when (val r = fsApi.listDirectory(fsApi.getRootDirectories().single())) {
        is IjentFileSystemApi.ListDirectory.Ok -> r.value

        is IjentFsResult.DoesNotExist,
        is IjentFsResult.NotDirectory,
        is IjentFsResult.NotFile,
        is IjentFsResult.PermissionDenied -> error(r)
      }

      result.shouldBeEmpty()
    }
  }
}