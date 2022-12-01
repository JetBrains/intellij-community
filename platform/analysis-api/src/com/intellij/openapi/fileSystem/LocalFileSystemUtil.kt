// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.fileSystem

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.file.CanonicalPathUtil.toNioPath
import com.intellij.openapi.file.IoFileUtil.toCanonicalPath
import com.intellij.openapi.file.NioFileUtil.toCanonicalPath
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path

@ApiStatus.Experimental
object LocalFileSystemUtil {

  private val fileSystem get() = LocalFileSystem.getInstance()

  @JvmStatic
  fun findFileOrDirectory(path: String): VirtualFile? {
    return VirtualFileSystemUtil.findFileOrDirectory(fileSystem, path)
  }

  @JvmStatic
  fun findFileOrDirectory(path: Path): VirtualFile? {
    return findFileOrDirectory(path.toCanonicalPath())
  }

  @JvmStatic
  fun findFileOrDirectory(file: File): VirtualFile? {
    return findFileOrDirectory(file.toCanonicalPath())
  }

  @JvmStatic
  fun getFileOrDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.getFileOrDirectory(fileSystem, path)
  }

  @JvmStatic
  fun getFileOrDirectory(path: Path): VirtualFile {
    return getFileOrDirectory(path.toCanonicalPath())
  }

  @JvmStatic
  fun getFileOrDirectory(file: File): VirtualFile {
    return getFileOrDirectory(file.toCanonicalPath())
  }

  @JvmStatic
  fun findFile(path: String): VirtualFile? {
    return VirtualFileSystemUtil.findFile(fileSystem, path)
  }

  @JvmStatic
  fun findFile(path: Path): VirtualFile? {
    return findFile(path.toCanonicalPath())
  }

  @JvmStatic
  fun findFile(file: File): VirtualFile? {
    return findFile(file.toCanonicalPath())
  }

  @JvmStatic
  fun getFile(path: String): VirtualFile {
    return VirtualFileSystemUtil.getFile(fileSystem, path)
  }

  @JvmStatic
  fun getFile(path: Path): VirtualFile {
    return getFile(path.toCanonicalPath())
  }

  @JvmStatic
  fun getFile(file: File): VirtualFile {
    return getFile(file.toCanonicalPath())
  }

  @JvmStatic
  fun findDirectory(path: String): VirtualFile? {
    return VirtualFileSystemUtil.findDirectory(fileSystem, path)
  }

  @JvmStatic
  fun findDirectory(path: Path): VirtualFile? {
    return findDirectory(path.toCanonicalPath())
  }

  @JvmStatic
  fun findDirectory(file: File): VirtualFile? {
    return findDirectory(file.toCanonicalPath())
  }

  @JvmStatic
  fun getDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.getDirectory(fileSystem, path)
  }

  @JvmStatic
  fun getDirectory(path: Path): VirtualFile {
    return getDirectory(path.toCanonicalPath())
  }

  @JvmStatic
  fun getDirectory(file: File): VirtualFile {
    return getDirectory(file.toCanonicalPath())
  }

  @JvmStatic
  fun findOrCreateFile(path: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateFile(fileSystem, path)
  }

  @JvmStatic
  fun findOrCreateFile(path: Path): VirtualFile {
    return findOrCreateFile(path.toCanonicalPath())
  }

  @JvmStatic
  fun findOrCreateDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateDirectory(fileSystem, path)
  }

  @JvmStatic
  fun findOrCreateDirectory(path: Path): VirtualFile {
    return findOrCreateDirectory(path.toCanonicalPath())
  }

  @JvmStatic
  fun createFile(path: String): VirtualFile {
    return VirtualFileSystemUtil.createFile(fileSystem, path)
  }

  @JvmStatic
  fun createFile(path: Path): VirtualFile {
    return createFile(path.toCanonicalPath())
  }

  @JvmStatic
  fun createDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.createDirectory(fileSystem, path)
  }

  @JvmStatic
  fun createDirectory(path: Path): VirtualFile {
    return createDirectory(path.toCanonicalPath())
  }

  @JvmStatic
  fun deleteFileOrDirectory(path: String) {
    VirtualFileSystemUtil.deleteFileOrDirectory(fileSystem, path)
  }

  @JvmStatic
  fun deleteFileOrDirectory(path: Path) {
    deleteFileOrDirectory(path.toCanonicalPath())
  }

  @JvmStatic
  fun deleteChildren(path: String, predicate: (VirtualFile) -> Boolean = { true }) {
    VirtualFileSystemUtil.deleteChildren(fileSystem, path, predicate)
  }

  @JvmStatic
  fun deleteChildren(path: Path, predicate: (VirtualFile) -> Boolean = { true }) {
    deleteChildren(path.toCanonicalPath(), predicate)
  }

  @JvmStatic
  fun refreshFiles(vararg paths: String, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshNioFiles(paths.map { it.toNioPath() }, async, recursive, callback)
  }

  @JvmStatic
  fun refreshFiles(vararg paths: Path, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshNioFiles(paths.toList(), async, recursive, callback)
  }

  @JvmStatic
  fun refreshFiles(vararg files: File, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshIoFiles(files.toList(), async, recursive, callback)
  }

  @JvmStatic
  fun refreshFiles(vararg files: VirtualFile, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshFiles(files.toList(), async, recursive, callback)
  }
}