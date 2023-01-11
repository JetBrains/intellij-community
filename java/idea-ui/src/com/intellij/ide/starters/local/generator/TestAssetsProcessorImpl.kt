// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.local.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.file.*
import com.intellij.openapi.file.NioPathUtil.getAbsoluteNioPath
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@Suppress("TestOnlyProblems")
fun convertOutputLocationForTests(moduleContentRoot: VirtualFile): Path {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return TestFileSystemLocation(moduleContentRoot, Path.of(moduleContentRoot.name))
  }
  return moduleContentRoot.toNioPath()
}

@TestOnly
class TestFileSystemLocation(
  val virtualFile: VirtualFile,
  /**
   * Fake Path for debug-purpose only, should never be used for disk operations
   */
  val debugPath: Path
) : Path by debugPath {

  override fun toString(): String {
    return "TestFileSystemLocation($debugPath)"
  }
}

@TestOnly
class TestAssetsProcessorImpl : AssetsProcessorImpl() {

  override fun writeText(file: Path, content: String) {
    if (file is TestFileSystemLocation) {
      file.virtualFile.writeText(content)
      return
    }
    super.writeText(file, content)
  }

  override fun writeBytes(file: Path, content: ByteArray) {
    if (file is TestFileSystemLocation) {
      file.virtualFile.writeBytes(content)
      return
    }
    super.writeBytes(file, content)
  }

  override fun findOrCreateFile(outputDirectory: Path, relativePath: String): Path {
    if (outputDirectory is TestFileSystemLocation) {
      val vFile = outputDirectory.virtualFile.findOrCreateVirtualFile(relativePath)
      val debugPath = outputDirectory.debugPath.getAbsoluteNioPath(relativePath)
      return TestFileSystemLocation(vFile, debugPath)
    }
    return super.findOrCreateFile(outputDirectory, relativePath)
  }

  override fun findOrCreateDirectory(outputDirectory: Path, relativePath: String): Path {
    if (outputDirectory is TestFileSystemLocation) {
      val vFile = outputDirectory.virtualFile.findOrCreateVirtualDirectory(relativePath)
      val debugPath = outputDirectory.debugPath.getAbsoluteNioPath(relativePath)
      return TestFileSystemLocation(vFile, debugPath)
    }
    return super.findOrCreateDirectory(outputDirectory, relativePath)
  }
}
