// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:JvmName("VirtualFileUtil")
package com.intellij.openapi.file

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.FileSystems
import java.nio.file.Path


val VirtualFile.isFile: Boolean
  get() = isValid && !isDirectory

fun VirtualFile.readText(): String {
  return VfsUtil.loadText(this)
}

fun VirtualFile.writeText(content: String) {
  VfsUtil.saveText(this, content)
}

fun VirtualFile.readBytes(): ByteArray {
  return inputStream.use { it.readBytes() }
}

fun VirtualFile.writeBytes(content: ByteArray) {
  setBinaryContent(content)
}

fun VirtualFile.toNioPathOrNull(): Path? {
  return runCatching { toNioPath() }.getOrNull()
}

fun VirtualFile.findDocument(): Document? {
  return FileDocumentManager.getInstance().getDocument(this)
}

fun VirtualFile.getDocument(): Document {
  return checkNotNull(findDocument()) {
    "Cannot find Document for $path"
  }
}

fun VirtualFile.reloadDocument() {
  val fileDocumentManager = FileDocumentManager.getInstance()
  val document = fileDocumentManager.getDocument(this)
  if (document != null) {
    fileDocumentManager.reloadFromDisk(document)
  }
}

fun VirtualFile.commitDocument(project: Project) {
  val psiDocumentManager = PsiDocumentManager.getInstance(project)
  val document = findDocument()
  if (document != null) {
    psiDocumentManager.commitDocument(document)
  }
}

fun VirtualFile.findPsiFile(project: Project): PsiFile? {
  return PsiManager.getInstance(project).findFile(this)
}

fun VirtualFile.getPsiFile(project: Project): PsiFile {
  return checkNotNull(findPsiFile(project)) {
    "Cannot find PSI file for $path"
  }
}

fun refreshVirtualFiles(vararg paths: Path, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
  RefreshQueue.getInstance().refresh(async, recursive, callback, paths.mapNotNull { it.findVirtualFileOrDirectory() })
}

fun refreshVirtualFiles(vararg files: VirtualFile, async: Boolean = false, recursive: Boolean = true, callback: () -> Unit = {}) {
  RefreshQueue.getInstance().refresh(async, recursive, callback, files.toList())
}

fun VirtualFile.findVirtualFileOrDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  return fileSystem.findVirtualFileOrDirectory(path.getResolvedPath(relativePath))
}

fun VirtualFile.getVirtualFileOrDirectory(relativePath: @SystemIndependent String): VirtualFile {
  return fileSystem.getVirtualFileOrDirectory(path.getResolvedPath(relativePath))
}

fun VirtualFile.findVirtualFile(relativePath: @SystemIndependent String): VirtualFile? {
  return fileSystem.findVirtualFile(path.getResolvedPath(relativePath))
}

fun VirtualFile.getVirtualFile(relativePath: @SystemIndependent String): VirtualFile {
  return fileSystem.getVirtualFile(path.getResolvedPath(relativePath))
}

fun VirtualFile.findVirtualDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  return fileSystem.findVirtualDirectory(path.getResolvedPath(relativePath))
}

fun VirtualFile.getVirtualDirectory(relativePath: @SystemIndependent String): VirtualFile {
  return fileSystem.getVirtualDirectory(path.getResolvedPath(relativePath))
}

fun VirtualFile.createVirtualFile(relativePath: @SystemIndependent String): VirtualFile {
  return fileSystem.createVirtualFile(path.getResolvedPath(relativePath))
}

fun VirtualFile.createVirtualDirectory(relativePath: @SystemIndependent String): VirtualFile {
  return fileSystem.createVirtualDirectory(path.getResolvedPath(relativePath))
}

fun VirtualFile.findOrCreateVirtualFile(relativePath: @SystemIndependent String): VirtualFile {
  return fileSystem.findOrCreateVirtualFile(path.getResolvedPath(relativePath))
}

fun VirtualFile.findOrCreateVirtualDirectory(relativePath: @SystemIndependent String): VirtualFile {
  return fileSystem.findOrCreateVirtualDirectory(path.getResolvedPath(relativePath))
}

fun VirtualFile.deleteVirtualFileOrDirectory() {
  fileSystem.deleteVirtualFileOrDirectory(path)
}

fun VirtualFile.deleteVirtualChildren(predicate: (VirtualFile) -> Boolean = { true }) {
  fileSystem.deleteVirtualChildren(path, predicate)
}

fun Path.findVirtualFileOrDirectory(): VirtualFile? {
  return getVirtualFileSystem().findVirtualFileOrDirectory(toCanonicalPath())
}

fun Path.getVirtualFileOrDirectory(): VirtualFile {
  return getVirtualFileSystem().getVirtualFileOrDirectory(toCanonicalPath())
}

fun Path.findVirtualFile(): VirtualFile? {
  return getVirtualFileSystem().findVirtualFile(toCanonicalPath())
}

fun Path.getVirtualFile(): VirtualFile {
  return getVirtualFileSystem().getVirtualFile(toCanonicalPath())
}

fun Path.findVirtualDirectory(): VirtualFile? {
  return getVirtualFileSystem().findVirtualDirectory(toCanonicalPath())
}

fun Path.getVirtualDirectory(): VirtualFile {
  return getVirtualFileSystem().getVirtualDirectory(toCanonicalPath())
}

fun Path.findOrCreateVirtualFile(): VirtualFile {
  return getVirtualFileSystem().findOrCreateVirtualFile(toCanonicalPath())
}

fun Path.createVirtualFile(): VirtualFile {
  return getVirtualFileSystem().createVirtualFile(toCanonicalPath())
}

fun Path.createVirtualDirectory(): VirtualFile {
  return getVirtualFileSystem().createVirtualDirectory(toCanonicalPath())
}

fun Path.findOrCreateVirtualDirectory(): VirtualFile {
  return getVirtualFileSystem().findOrCreateVirtualDirectory(toCanonicalPath())
}

fun Path.deleteVirtualFileOrDirectory() {
  getVirtualFileSystem().deleteVirtualFileOrDirectory(toCanonicalPath())
}

fun Path.deleteVirtualChildren(predicate: (VirtualFile) -> Boolean = { true }) {
  getVirtualFileSystem().deleteVirtualChildren(toCanonicalPath(), predicate)
}

private fun Path.getVirtualFileSystem(): VirtualFileSystem {
  check(FileSystems.getDefault() == fileSystem) {
    "Unsupported non default file system: $fileSystem"
  }
  val fileSystemManager = VirtualFileManager.getInstance()
  val fileSystem = fileSystemManager.getFileSystem(StandardFileSystems.FILE_PROTOCOL)
  return checkNotNull(fileSystem) {
    "Cannot find standard file system"
  }
}

fun VirtualFileSystem.findVirtualFileOrDirectory(path: @SystemIndependent String): VirtualFile? {
  return refreshAndFindFileByPath(path)
}

fun VirtualFileSystem.getVirtualFileOrDirectory(path: @SystemIndependent String): VirtualFile {
  return checkNotNull(findVirtualFileOrDirectory(path)) {
    "File or directory doesn't exist: $path"
  }
}

fun VirtualFileSystem.findVirtualFile(path: @SystemIndependent String): VirtualFile? {
  val file = findVirtualFileOrDirectory(path) ?: return null
  check(file.isFile) {
    "Expected file instead of directory: $path"
  }
  return file
}

fun VirtualFileSystem.getVirtualFile(path: @SystemIndependent String): VirtualFile {
  return checkNotNull(findVirtualFile(path)) {
    "File doesn't exist: $path"
  }
}

fun VirtualFileSystem.findVirtualDirectory(path: @SystemIndependent String): VirtualFile? {
  val file = findVirtualFileOrDirectory(path) ?: return null
  check(file.isDirectory) {
    "Expected directory instead of file: $path"
  }
  return file
}

fun VirtualFileSystem.getVirtualDirectory(path: @SystemIndependent String): VirtualFile {
  return checkNotNull(findVirtualDirectory(path)) {
    "Directory doesn't exist: $path"
  }
}

fun VirtualFileSystem.createVirtualFile(path: @SystemIndependent String): VirtualFile {
  val parentFile = findOrCreateParentDirectory(path)
  return parentFile.createChildData(null, path.getFileName())
}

fun VirtualFileSystem.createVirtualDirectory(path: @SystemIndependent String): VirtualFile {
  val parentFile = findOrCreateParentDirectory(path)
  return parentFile.createChildDirectory(null, path.getFileName())
}

fun VirtualFileSystem.findOrCreateVirtualFile(path: @SystemIndependent String): VirtualFile {
  return findVirtualFile(path) ?: createVirtualFile(path)
}

fun VirtualFileSystem.findOrCreateVirtualDirectory(path: @SystemIndependent String): VirtualFile {
  return findVirtualDirectory(path) ?: createVirtualDirectory(path)
}

fun VirtualFileSystem.deleteVirtualFileOrDirectory(path: @SystemIndependent String) {
  val file = findVirtualFileOrDirectory(path) ?: return
  file.delete(this)
}

fun VirtualFileSystem.deleteVirtualChildren(path: @SystemIndependent String, predicate: (VirtualFile) -> Boolean = { true }) {
  val directory = getVirtualDirectory(path)
  for (child in directory.children) {
    if (predicate(child)) {
      child.delete(this)
    }
  }
}

private fun VirtualFileSystem.findOrCreateParentDirectory(path: @SystemIndependent String): VirtualFile {
  val parentPath = path.getParentPath()
  if (parentPath == null) {
    return getVirtualDirectory("/")
  }
  return findOrCreateVirtualDirectory(parentPath)
}
