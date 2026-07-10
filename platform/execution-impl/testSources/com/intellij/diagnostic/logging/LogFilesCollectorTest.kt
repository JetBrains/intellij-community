// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.directoryContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.pathString

class LogFilesCollectorTest {
  @Test
  fun `collect files in root`() {
    val logDir = directoryContent {
      file("main.log")
      file("main2.log")
    }.generateInTempDir()
    val root = logDir.pathString

    assertLogs("*")
    assertLogs("**/**")
    assertLogs("${root}/x.log")

    assertLogs("${root}/main.log", "${root}/main.log")
    assertLogs("${root}/*.log", "${root}/main.log", "${root}/main2.log")
  }

  @Test
  fun `collect files in subdirectories`() {
    val logDir = directoryContent {
      dir("subDir1") {
        dir("deepSubDir") {
          file("file1.log")
          file("file2.log")
        }
        file("file1.log")
      }
      dir("subDir2") {
        file("file1.log")
        file("file2.log")
      }
    }.generateInTempDir()
    val root = logDir.pathString

    assertLogs("${root}/*/*.log", "${root}/subDir1/file1.log", "${root}/subDir2/file1.log", "${root}/subDir2/file2.log")
    assertLogs("${root}/*/file1.log", "${root}/subDir1/file1.log", "${root}/subDir2/file1.log")
    assertLogs("${root}/**/file1.log", "${root}/subDir1/deepSubDir/file1.log", "${root}/subDir1/file1.log", "${root}/subDir2/file1.log")
  }

  private fun assertLogs(pathPattern: String, vararg expected: String) {
    assertThat(collectLogPaths(FileUtil.toSystemDependentName(pathPattern), includeAll = true))
      .containsExactlyInAnyOrderElementsOf(expected.map { FileUtil.toSystemDependentName(it) })
  }
}
