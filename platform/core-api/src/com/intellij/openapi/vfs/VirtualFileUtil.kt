// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualFileUtil")

package com.intellij.openapi.vfs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import org.jetbrains.annotations.SystemIndependent
import java.io.IOException
import java.nio.file.Path


val VirtualFile.isFile: Boolean
  get() = isValid && !isDirectory

fun VirtualFile.readText(): String {
  return VfsUtilCore.loadText(this)
}

fun VirtualFile.writeText(content: String) {
  VfsUtilCore.saveText(this, content)
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

fun VirtualFile.reloadDocument() {
  val document = findDocument() ?: return
  FileDocumentManager.getInstance().reloadFromDisk(document)
}

fun VirtualFile.commitDocument(project: Project) {
  val document = findDocument() ?: return
  PsiDocumentManager.getInstance(project).commitDocument(document)
}

fun Document.findVirtualFile(): VirtualFile? {
  return FileDocumentManager.getInstance().getFile(this)
}

fun Document.saveDocument() {
  FileDocumentManager.getInstance().saveDocument(this)
}

fun VirtualFile.findPsiFile(project: Project): PsiFile? {
  return PsiManager.getInstance(project).findFile(this)
}

private fun VirtualFile.findFileOrDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  return fileSystem.findFileByPath(path.getResolvedPath(relativePath))
}

fun VirtualFile.findFile(relativePath: @SystemIndependent String): VirtualFile? {
  val file = findFileOrDirectory(relativePath) ?: return null
  if (!file.isFile) {
    throw IOException("Expected file instead of directory: $path/$relativePath")
  }
  return file
}

fun VirtualFile.findDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  val file = findFileOrDirectory(relativePath) ?: return null
  if (!file.isDirectory) {
    throw IOException("Expected directory instead of file: $path/$relativePath")
  }
  return file
}

fun VirtualFile.findOrCreateFile(relativePath: @SystemIndependent String): VirtualFile {
  val directory = findOrCreateDirectory("$relativePath/..")
  val name = path.getResolvedPath(relativePath).getFileName()
  val file = directory.findChild(name) ?: directory.createChildData(fileSystem, name)
  if (!file.isFile) {
    throw IOException("Expected file instead of directory: $path/$relativePath")
  }
  return file
}

fun VirtualFile.findOrCreateDirectory(relativePath: @SystemIndependent String): VirtualFile {
  val path = path.getResolvedPath(relativePath)
  var directory = checkNotNull(fileSystem.findFileByPath("/")) {
    "Cannot find file system root for file: $path"
  }
  for (name in path.removePrefix("/").split("/")) {
    directory = directory.findChild(name) ?: directory.createChildDirectory(fileSystem, name)
    if (!directory.isDirectory) {
      throw IOException("Expected directory instead of file: ${directory.path}")
    }
  }
  return directory
}

fun VirtualFile.deleteRecursively() {
  delete(fileSystem)
}

fun VirtualFile.deleteChildrenRecursively(predicate: (VirtualFile) -> Boolean) {
  children.filter(predicate).forEach { it.delete(fileSystem) }
}

fun VirtualFile.deleteRecursively(relativePath: String) {
  findFileOrDirectory(relativePath)?.deleteRecursively()
}

fun VirtualFile.deleteChildrenRecursively(relativePath: String, predicate: (VirtualFile) -> Boolean) {
  findFileOrDirectory(relativePath)?.deleteChildrenRecursively(predicate)
}

fun Path.findVirtualFile(): VirtualFile? {
  val fileManager = VirtualFileManager.getInstance()
  val file = fileManager.refreshAndFindFileByNioPath(this) ?: return null
  if (!file.isFile) {
    throw IOException("Expected file instead of directory: $this")
  }
  return file
}

fun Path.findVirtualDirectory(): VirtualFile? {
  val fileManager = VirtualFileManager.getInstance()
  val file = fileManager.refreshAndFindFileByNioPath(this) ?: return null
  if (!file.isDirectory) {
    throw IOException("Expected directory instead of file: $this")
  }
  return file
}

object VirtualFilePrefixTreeFactory : AbstractPrefixTreeFactory<VirtualFile, String>() {

  override fun convertToList(element: VirtualFile): List<String> {
    return CanonicalPathPrefixTreeFactory.convertToList(element.path)
  }
}
