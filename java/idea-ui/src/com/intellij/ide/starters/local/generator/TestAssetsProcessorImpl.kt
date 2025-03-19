// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.local.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.vfs.*
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

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

  override fun writeText(path: Path, content: String) {
    if (path is TestFileSystemLocation) {
      WriteAction.runAndWait<Throwable> {
        path.virtualFile.writeText(content)
      }
      return
    }
    super.writeText(path, content)
  }

  override fun writeBytes(path: Path, content: ByteArray) {
    if (path is TestFileSystemLocation) {
      WriteAction.runAndWait<Throwable> {
        path.virtualFile.writeBytes(content)
      }
      return
    }
    super.writeBytes(path, content)
  }

  override fun findOrCreateFile(path: Path, relativePath: String): Path {
    if (path is TestFileSystemLocation) {
      val vFile = WriteAction.computeAndWait<VirtualFile, Throwable> {
        path.virtualFile.findOrCreateFile(relativePath)
      }
      val debugPath = path.debugPath.getResolvedPath(relativePath)
      return TestFileSystemLocation(vFile, debugPath)
    }
    return super.findOrCreateFile(path, relativePath)
  }

  override fun findOrCreateDirectory(path: Path, relativePath: String): Path {
    if (path is TestFileSystemLocation) {
      val vFile = WriteAction.computeAndWait<VirtualFile, Throwable> {
        path.virtualFile.findOrCreateDirectory(relativePath)
      }
      val debugPath = path.debugPath.getResolvedPath(relativePath)
      return TestFileSystemLocation(vFile, debugPath)
    }
    return super.findOrCreateDirectory(path, relativePath)
  }

  override fun addPosixFilePermissions(path: Path, permissions: Set<PosixFilePermission>) {
    if (path !is TestFileSystemLocation) {
      super.addPosixFilePermissions(path, permissions)
    }
  }
}
