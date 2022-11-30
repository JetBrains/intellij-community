// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.intellij.openapi.fileSystem

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.file.CanonicalPathUtil.getFileName
import com.intellij.openapi.file.CanonicalPathUtil.getParentPath
import com.intellij.openapi.file.NioFileUtil.toCanonicalPath
import java.nio.file.Path

object VirtualFileSystemUtil {

  fun findFileOrDirectory(fileSystem: VirtualFileSystem, path: String): VirtualFile? {
    return fileSystem.refreshAndFindFileByPath(path)
  }

  fun findFileOrDirectory(fileSystem: VirtualFileSystem, path: Path): VirtualFile? {
    return findFileOrDirectory(fileSystem, path.toCanonicalPath())
  }

  fun getFileOrDirectory(fileSystem: VirtualFileSystem, path: String): VirtualFile {
    return requireNotNull(findFileOrDirectory(fileSystem, path)) { "File or directory doesn't exist: $path" }
  }

  fun getFileOrDirectory(fileSystem: VirtualFileSystem, path: Path): VirtualFile {
    return getFileOrDirectory(fileSystem, path.toCanonicalPath())
  }

  fun findFile(fileSystem: VirtualFileSystem, path: String): VirtualFile? {
    val file = findFileOrDirectory(fileSystem, path) ?: return null
    require(!file.isDirectory) { "Expected file instead directory: $path" }
    return file
  }

  fun findFile(fileSystem: VirtualFileSystem, path: Path): VirtualFile? {
    return findFile(fileSystem, path.toCanonicalPath())
  }

  fun getFile(fileSystem: VirtualFileSystem, path: String): VirtualFile {
    return requireNotNull(findFile(fileSystem, path)) { "File doesn't exist: $path" }
  }

  fun getFile(fileSystem: VirtualFileSystem, path: Path): VirtualFile {
    return getFile(fileSystem, path.toCanonicalPath())
  }

  fun findDirectory(fileSystem: VirtualFileSystem, path: String): VirtualFile? {
    val file = findFileOrDirectory(fileSystem, path) ?: return null
    require(file.isDirectory) { "Expected directory instead file: $path" }
    return file
  }

  fun findDirectory(fileSystem: VirtualFileSystem, path: Path): VirtualFile? {
    return findDirectory(fileSystem, path.toCanonicalPath())
  }

  fun getDirectory(fileSystem: VirtualFileSystem, path: String): VirtualFile {
    return requireNotNull(findDirectory(fileSystem, path)) { "Directory doesn't exist: $path" }
  }

  fun getDirectory(fileSystem: VirtualFileSystem, path: Path): VirtualFile {
    return getDirectory(fileSystem, path.toCanonicalPath())
  }

  fun findOrCreateFile(fileSystem: VirtualFileSystem, path: String): VirtualFile {
    return findFile(fileSystem, path) ?: createFile(fileSystem, path)
  }

  fun findOrCreateFile(fileSystem: VirtualFileSystem, path: Path): VirtualFile {
    return findOrCreateFile(fileSystem, path.toCanonicalPath())
  }

  fun findOrCreateDirectory(fileSystem: VirtualFileSystem, path: String): VirtualFile {
    return findDirectory(fileSystem, path) ?: createDirectory(fileSystem, path)
  }

  fun findOrCreateDirectory(fileSystem: VirtualFileSystem, path: Path): VirtualFile {
    return findOrCreateDirectory(fileSystem, path.toCanonicalPath())
  }

  fun createFile(fileSystem: VirtualFileSystem, path: String): VirtualFile {
    val parentPath = path.getParentPath()
    requireNotNull(parentPath) { "Cannot create FS root. Use findDirectory instead. $path" }
    val parentFile = findOrCreateDirectory(fileSystem, parentPath)
    return parentFile.createChildData(null, path.getFileName())
  }

  fun createFile(fileSystem: VirtualFileSystem, path: Path): VirtualFile {
    return createFile(fileSystem, path.toCanonicalPath())
  }

  fun createDirectory(fileSystem: VirtualFileSystem, path: String): VirtualFile {
    val parentPath = path.getParentPath()
    requireNotNull(parentPath) { "Cannot create FS root. Use findDirectory instead. $path" }
    val parentFile = findOrCreateDirectory(fileSystem, parentPath)
    return parentFile.createChildDirectory(null, path.getFileName())
  }

  fun createDirectory(fileSystem: VirtualFileSystem, path: Path): VirtualFile {
    return createDirectory(fileSystem, path.toCanonicalPath())
  }

  fun deleteFileOrDirectory(fileSystem: VirtualFileSystem, path: String) {
    val file = findFileOrDirectory(fileSystem, path) ?: return
    file.delete(fileSystem)
  }

  fun deleteFileOrDirectory(fileSystem: VirtualFileSystem, path: Path) {
    deleteFileOrDirectory(fileSystem, path.toCanonicalPath())
  }

  fun deleteChildren(fileSystem: VirtualFileSystem, path: String, predicate: (VirtualFile) -> Boolean = { true }) {
    val directory = getDirectory(fileSystem, path)
    for (child in directory.children) {
      if (predicate(child)) {
        child.delete(fileSystem)
      }
    }
  }

  fun deleteChildren(fileSystem: VirtualFileSystem, path: Path, predicate: (VirtualFile) -> Boolean = { true }) {
    deleteChildren(fileSystem, path.toCanonicalPath(), predicate)
  }
}