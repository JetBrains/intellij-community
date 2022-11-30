// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.fileSystem

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.file.CanonicalPathUtil.toNioPath
import com.intellij.openapi.file.NioFileUtil.toCanonicalPath
import java.io.File
import java.nio.file.Path

object LocalFileSystemUtil {

  private val fileSystem get() = LocalFileSystem.getInstance()

  fun findFileOrDirectory(path: String): VirtualFile? {
    return VirtualFileSystemUtil.findFileOrDirectory(fileSystem, path)
  }

  fun findFileOrDirectory(path: Path): VirtualFile? {
    return findFileOrDirectory(path.toCanonicalPath())
  }

  fun findFileOrDirectory(file: File): VirtualFile? {
    return findFileOrDirectory(file.path)
  }

  fun getFileOrDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.getFileOrDirectory(fileSystem, path)
  }

  fun getFileOrDirectory(path: Path): VirtualFile {
    return getFileOrDirectory(path.toCanonicalPath())
  }

  fun getFileOrDirectory(file: File): VirtualFile {
    return getFileOrDirectory(file.path)
  }

  fun findFile(path: String): VirtualFile? {
    return VirtualFileSystemUtil.findFile(fileSystem, path)
  }

  fun findFile(path: Path): VirtualFile? {
    return findFile(path.toCanonicalPath())
  }

  fun findFile(file: File): VirtualFile? {
    return findFile(file.path)
  }

  fun getFile(path: String): VirtualFile {
    return VirtualFileSystemUtil.getFile(fileSystem, path)
  }

  fun getFile(path: Path): VirtualFile {
    return getFile(path.toCanonicalPath())
  }

  fun getFile(file: File): VirtualFile {
    return getFile(file.path)
  }

  fun findDirectory(path: String): VirtualFile? {
    return VirtualFileSystemUtil.findDirectory(fileSystem, path)
  }

  fun findDirectory(path: Path): VirtualFile? {
    return findDirectory(path.toCanonicalPath())
  }

  fun findDirectory(file: File): VirtualFile? {
    return findDirectory(file.path)
  }

  fun getDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.getDirectory(fileSystem, path)
  }

  fun getDirectory(path: Path): VirtualFile {
    return getDirectory(path.toCanonicalPath())
  }

  fun getDirectory(file: File): VirtualFile {
    return getDirectory(file.path)
  }

  fun findOrCreateFile(path: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateFile(fileSystem, path)
  }

  fun findOrCreateFile(path: Path): VirtualFile {
    return findOrCreateFile(path.toCanonicalPath())
  }

  fun findOrCreateDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateDirectory(fileSystem, path)
  }

  fun findOrCreateDirectory(path: Path): VirtualFile {
    return findOrCreateDirectory(path.toCanonicalPath())
  }

  fun createFile(path: String): VirtualFile {
    return VirtualFileSystemUtil.createFile(fileSystem, path)
  }

  fun createFile(path: Path): VirtualFile {
    return createFile(path.toCanonicalPath())
  }

  fun createDirectory(path: String): VirtualFile {
    return VirtualFileSystemUtil.createDirectory(fileSystem, path)
  }

  fun createDirectory(path: Path): VirtualFile {
    return createDirectory(path.toCanonicalPath())
  }

  fun deleteFileOrDirectory(path: String) {
    VirtualFileSystemUtil.deleteFileOrDirectory(fileSystem, path)
  }

  fun deleteFileOrDirectory(path: Path) {
    deleteFileOrDirectory(path.toCanonicalPath())
  }

  fun deleteChildren(path: String, predicate: (VirtualFile) -> Boolean = { true }) {
    VirtualFileSystemUtil.deleteChildren(fileSystem, path, predicate)
  }

  fun deleteChildren(path: Path, predicate: (VirtualFile) -> Boolean = { true }) {
    deleteChildren(path.toCanonicalPath(), predicate)
  }

  fun refreshFiles(vararg paths: String, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshNioFiles(paths.map { it.toNioPath() }, async, recursive, callback)
  }

  fun refreshFiles(vararg paths: Path, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshNioFiles(paths.toList(), async, recursive, callback)
  }

  fun refreshFiles(vararg files: File, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshIoFiles(files.toList(), async, recursive, callback)
  }

  fun refreshFiles(vararg files: VirtualFile, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
    fileSystem.refreshFiles(files.toList(), async, recursive, callback)
  }
}