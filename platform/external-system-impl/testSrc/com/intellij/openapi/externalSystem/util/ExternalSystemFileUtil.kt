// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
@file:ApiStatus.Experimental

package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.PathUtil
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


fun VirtualFile.findFileOrDirectory(relativePath: String) =
  fileSystem.findFileOrDirectory(getAbsolutePath(relativePath))

fun VirtualFile.getFileOrDirectory(relativePath: String) =
  fileSystem.getFileOrDirectory(getAbsolutePath(relativePath))

fun VirtualFile.findFile(relativePath: String) =
  fileSystem.findFile(getAbsolutePath(relativePath))

fun VirtualFile.getFile(relativePath: String) =
  fileSystem.getFile(getAbsolutePath(relativePath))

fun VirtualFile.findDirectory(relativePath: String) =
  fileSystem.findDirectory(getAbsolutePath(relativePath))

fun VirtualFile.getDirectory(relativePath: String) =
  fileSystem.getDirectory(getAbsolutePath(relativePath))

fun VirtualFile.findOrCreateFile(relativePath: String) =
  fileSystem.findOrCreateFile(getAbsolutePath(relativePath))

fun VirtualFile.findOrCreateDirectory(relativePath: String) =
  fileSystem.findOrCreateDirectory(getAbsolutePath(relativePath))

fun VirtualFile.createFile(relativePath: String) =
  fileSystem.createFile(getAbsolutePath(relativePath))

fun VirtualFile.createDirectory(relativePath: String) =
  fileSystem.createDirectory(getAbsolutePath(relativePath))

fun VirtualFile.deleteFileOrDirectory(relativePath: String = ".") =
  fileSystem.deleteFileOrDirectory(getAbsolutePath(relativePath))

fun VirtualFile.deleteChildren(relativePath: String = ".", predicate: (VirtualFile) -> Boolean = { true }) =
  fileSystem.deleteChildren(getAbsolutePath(relativePath), predicate)

fun VirtualFileSystem.findFileOrDirectory(path: Path) =
  findFileOrDirectory(path.systemIndependentPath)

fun VirtualFileSystem.getFileOrDirectory(path: Path) =
  getFileOrDirectory(path.systemIndependentPath)

fun VirtualFileSystem.findFile(path: Path) =
  findFile(path.systemIndependentPath)

fun VirtualFileSystem.getFile(path: Path) =
  getFile(path.systemIndependentPath)

fun VirtualFileSystem.findDirectory(path: Path) =
  findDirectory(path.systemIndependentPath)

fun VirtualFileSystem.getDirectory(path: Path) =
  getDirectory(path.systemIndependentPath)

fun VirtualFileSystem.findOrCreateFile(path: Path) =
  findOrCreateFile(path.systemIndependentPath)

fun VirtualFileSystem.findOrCreateDirectory(path: Path) =
  findOrCreateDirectory(path.systemIndependentPath)

fun VirtualFileSystem.createFile(path: Path) =
  createFile(path.systemIndependentPath)

fun VirtualFileSystem.createDirectory(path: Path) =
  createDirectory(path.systemIndependentPath)

fun VirtualFileSystem.deleteFileOrDirectory(path: Path) =
  deleteFileOrDirectory(path.systemIndependentPath)

fun VirtualFileSystem.deleteChildren(path: Path, predicate: (VirtualFile) -> Boolean = { true }) =
  deleteChildren(path.systemIndependentPath, predicate)

fun VirtualFile.loadText(): String = VfsUtil.loadText(this)

fun VirtualFile.saveText(text: String) = VfsUtil.saveText(this, text)

fun VirtualFileSystem.findFileOrDirectory(path: String): VirtualFile? {
  return refreshAndFindFileByPath(path)
}

fun VirtualFileSystem.getFileOrDirectory(path: String): VirtualFile {
  return requireNotNull(findFileOrDirectory(path)) { "File or directory doesn't exist: $path" }
}

fun VirtualFileSystem.findFile(path: String): VirtualFile? {
  val file = findFileOrDirectory(path) ?: return null
  require(!file.isDirectory) { "Expected file instead directory: $path" }
  return file
}

fun VirtualFileSystem.getFile(path: String): VirtualFile {
  return requireNotNull(findFile(path)) { "File doesn't exist: $path" }
}

fun VirtualFileSystem.findDirectory(path: String): VirtualFile? {
  val file = findFileOrDirectory(path) ?: return null
  require(file.isDirectory) { "Expected directory instead file: $path" }
  return file
}

fun VirtualFileSystem.getDirectory(path: String): VirtualFile {
  return requireNotNull(findDirectory(path)) { "Directory doesn't exist: $path" }
}

fun VirtualFileSystem.findOrCreateFile(path: String): VirtualFile {
  return findFile(path) ?: createFile(path)
}

fun VirtualFileSystem.findOrCreateDirectory(path: String): VirtualFile {
  return findDirectory(path) ?: createDirectory(path)
}

fun VirtualFileSystem.createFile(path: String): VirtualFile {
  val parentPath = path.getParentPath()
  requireNotNull(parentPath) { "Cannot create FS root. Use findDirectory instead. $path" }
  val parentFile = findOrCreateDirectory(parentPath)
  return parentFile.createChildData(null, path.getPathName())
}

fun VirtualFileSystem.createDirectory(path: String): VirtualFile {
  val parentPath = path.getParentPath()
  requireNotNull(parentPath) { "Cannot create FS root. Use findDirectory instead. $path" }
  val parentFile = findOrCreateDirectory(parentPath)
  return parentFile.createChildDirectory(null, path.getPathName())
}

fun VirtualFileSystem.deleteFileOrDirectory(path: String) {
  findFileOrDirectory(path)?.delete(null)
}

fun VirtualFileSystem.deleteChildren(path: String, predicate: (VirtualFile) -> Boolean = { true }) {
  val directory = getDirectory(path)
  for (child in directory.children) {
    if (predicate(child)) {
      child.delete(null)
    }
  }
}

fun File.refreshInLfs(async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
  toPath().refreshInLfs(async, recursive, callback)
}

fun Path.refreshInLfs(async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
  val fileSystem = LocalFileSystem.getInstance()
  fileSystem.refreshNioFiles(listOf(this), async, recursive, callback)
}

fun VirtualFile.reloadFromDisk() {
  val fileDocumentManager = FileDocumentManager.getInstance()
  val document = fileDocumentManager.getCachedDocument(this) ?: return
  fileDocumentManager.reloadFromDisk(document)
}

fun String.toNioPath(): Path {
  return Paths.get(FileUtil.toSystemDependentName(this))
}

fun String.getPathName(): String {
  return PathUtil.getFileName(this)
}

fun String.getParentPath(): String? {
  return PathUtil.getParentPath(this).nullize()
}

fun String.getAbsolutePath(relativePath: String): String {
  val path = "$this/$relativePath"
  return FileUtil.toCanonicalPath(path) // resolve simple symlinks . and ..
}

fun String.getRelativePath(path: String): String? {
  return FileUtil.getRelativePath(this, path, '/')
}

fun String.getAbsoluteNioPath(relativePath: String): Path {
  return getAbsolutePath(relativePath).toNioPath()
}

fun String.getRelativeNioPath(path: String): Path? {
  return getRelativePath(path)?.toNioPath()
}

fun Path.getAbsolutePath(relativePath: String): String {
  return systemIndependentPath.getAbsolutePath(relativePath)
}

fun Path.getRelativePath(path: Path): String? {
  return systemIndependentPath.getRelativePath(path.systemIndependentPath)
}

fun Path.getAbsoluteNioPath(relativePath: String): Path {
  return systemIndependentPath.getAbsoluteNioPath(relativePath)
}

fun Path.getRelativeNioPath(path: Path): Path? {
  return systemIndependentPath.getRelativeNioPath(path.systemIndependentPath)
}

fun VirtualFile.getAbsolutePath(relativePath: String): String {
  return path.getAbsolutePath(relativePath)
}

fun VirtualFile.getRelativePath(file: VirtualFile): String? {
  return path.getRelativePath(file.path)
}

fun VirtualFile.getAbsoluteNioPath(relativePath: String): Path {
  return path.getAbsoluteNioPath(relativePath)
}

fun VirtualFile.getRelativeNioPath(file: VirtualFile): Path? {
  return path.getRelativeNioPath(file.path)
}
