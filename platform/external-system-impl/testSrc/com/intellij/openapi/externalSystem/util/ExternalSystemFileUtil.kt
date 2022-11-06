// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.io.systemIndependentPath
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name


fun VirtualFile.findFileOrDirectory(relativePath: String) =
  fileSystem.findFileOrDirectory(getAbsoluteNioPath(relativePath))

fun VirtualFile.getFileOrDirectory(relativePath: String) =
  fileSystem.getFileOrDirectory(getAbsoluteNioPath(relativePath))

fun VirtualFile.findFile(relativePath: String) =
  fileSystem.findFile(getAbsoluteNioPath(relativePath))

fun VirtualFile.getFile(relativePath: String) =
  fileSystem.getFile(getAbsoluteNioPath(relativePath))

fun VirtualFile.findDirectory(relativePath: String) =
  fileSystem.findDirectory(getAbsoluteNioPath(relativePath))

fun VirtualFile.getDirectory(relativePath: String) =
  fileSystem.getDirectory(getAbsoluteNioPath(relativePath))

fun VirtualFile.findOrCreateFile(relativePath: String) =
  fileSystem.findOrCreateFile(getAbsoluteNioPath(relativePath))

fun VirtualFile.findOrCreateDirectory(relativePath: String) =
  fileSystem.findOrCreateDirectory(getAbsoluteNioPath(relativePath))

fun VirtualFile.createFile(relativePath: String) =
  fileSystem.createFile(getAbsoluteNioPath(relativePath))

fun VirtualFile.createDirectory(relativePath: String) =
  fileSystem.createDirectory(getAbsoluteNioPath(relativePath))

fun VirtualFile.deleteFileOrDirectory(relativePath: String = ".") =
  fileSystem.deleteFileOrDirectory(getAbsoluteNioPath(relativePath))

fun VirtualFile.deleteChildren(relativePath: String = ".", predicate: (VirtualFile) -> Boolean = { true }) =
  fileSystem.deleteChildren(getAbsoluteNioPath(relativePath), predicate)

fun VirtualFileSystem.findFileOrDirectory(path: Path): VirtualFile? {
  return refreshAndFindFileByPath(path.toString())
}

fun VirtualFileSystem.getFileOrDirectory(path: Path): VirtualFile {
  return requireNotNull(findFileOrDirectory(path)) { "File or directory doesn't exist: $path" }
}

fun VirtualFileSystem.findFile(path: Path): VirtualFile? {
  val file = findFileOrDirectory(path) ?: return null
  require(!file.isDirectory) { "Expected file instead directory: $path" }
  return file
}

fun VirtualFileSystem.getFile(path: Path): VirtualFile {
  return requireNotNull(findFile(path)) { "File doesn't exist: $path" }
}

fun VirtualFileSystem.findDirectory(path: Path): VirtualFile? {
  val file = findFileOrDirectory(path) ?: return null
  require(file.isDirectory) { "Expected directory instead file: $path" }
  return file
}

fun VirtualFileSystem.getDirectory(path: Path): VirtualFile {
  return requireNotNull(findDirectory(path)) { "Directory doesn't exist: $path" }
}

fun VirtualFileSystem.findOrCreateFile(path: Path): VirtualFile {
  return findFile(path) ?: createFile(path)
}

fun VirtualFileSystem.findOrCreateDirectory(path: Path): VirtualFile {
  return findDirectory(path) ?: createDirectory(path)
}

fun VirtualFileSystem.createFile(path: Path): VirtualFile {
  require(path.nameCount > 0) { "Cannot create FS root. Use findOrCreateFile instead" }
  val parentFile = findOrCreateDirectory(path.parent)
  return parentFile.createChildData(null, path.name)
}

fun VirtualFileSystem.createDirectory(path: Path): VirtualFile {
  require(path.nameCount > 0) { "Cannot create FS root. Use findOrCreateDirectory instead" }
  val parentFile = findOrCreateDirectory(path.parent)
  return parentFile.createChildDirectory(null, path.name)
}

fun VirtualFileSystem.deleteFileOrDirectory(path: Path) {
  findFileOrDirectory(path)?.delete(null)
}

fun VirtualFileSystem.deleteChildren(path: Path, predicate: (VirtualFile) -> Boolean = { true }) {
  val directory = getDirectory(path)
  for (child in directory.children) {
    if (predicate(child)) {
      child.delete(null)
    }
  }
}

fun VirtualFile.loadText(): String = VfsUtil.loadText(this)

fun VirtualFile.saveText(text: String) = VfsUtil.saveText(this, text)

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

fun Path.getAbsoluteNioPath(relativePath: String): Path {
  val path = "$systemIndependentPath/$relativePath"
  val canonicalPath = FileUtil.toCanonicalPath(path) // resolve simple symlinks . and ..
  return Paths.get(FileUtil.toSystemDependentName(canonicalPath))
}

fun VirtualFile.getAbsoluteNioPath(relativePath: String): Path {
  return toNioPath().getAbsoluteNioPath(relativePath)
}

fun VirtualFile.getRelativeNioPath(path: Path): Path {
  return toNioPath().relativize(path)
}

fun Path.getAbsolutePath(relativePath: String): String {
  return getAbsoluteNioPath(relativePath).systemIndependentPath
}

fun VirtualFile.getAbsolutePath(relativePath: String): String {
  return getAbsoluteNioPath(relativePath).systemIndependentPath
}

fun VirtualFile.getRelativePath(path: Path): String {
  return getRelativeNioPath(path).systemIndependentPath
}
