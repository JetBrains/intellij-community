// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualFileUtil")

package com.intellij.openapi.vfs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.*
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import org.jetbrains.annotations.SystemIndependent
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString


val VirtualFile.isFile: Boolean
  get() = isValid && !isDirectory

fun VirtualFile.readText(): String {
  return VfsUtilCore.loadText(this)
}

@RequiresWriteLock
fun VirtualFile.writeText(content: String) {
  VfsUtilCore.saveText(this, content)
}

fun VirtualFile.readBytes(): ByteArray {
  return inputStream.use { it.readBytes() }
}

@RequiresWriteLock
fun VirtualFile.writeBytes(content: ByteArray) {
  setBinaryContent(content)
}

fun VirtualFile.toNioPathOrNull(): Path? {
  return runCatching { toNioPath() }.getOrNull()
}

@RequiresReadLock
fun VirtualFile.findDocument(): Document? {
  return FileDocumentManager.getInstance().getDocument(this)
}

@RequiresReadLock
fun VirtualFile.findPsiFile(project: Project): PsiFile? {
  return PsiManager.getInstance(project).findFile(this)
}

@RequiresReadLock
fun VirtualFile.findFileOrDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  var virtualFile = checkNotNull(fileSystem.findFileByPath("/")) {
    "Cannot find file system root for file: $path/$relativePath"
  }
  val resolvedPath = path.toNioPath().getResolvedPath(relativePath)
  for (pathPart in resolvedPath) {
    val name = pathPart.pathString
    virtualFile = virtualFile.findChild(name) ?: return null
  }
  return virtualFile
}

@RequiresReadLock
fun VirtualFile.findFile(relativePath: @SystemIndependent String): VirtualFile? {
  val file = findFileOrDirectory(relativePath) ?: return null
  if (!file.isFile) {
    throw IOException("Expected file instead of directory: $path/$relativePath")
  }
  return file
}

@RequiresReadLock
fun VirtualFile.findDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  val file = findFileOrDirectory(relativePath) ?: return null
  if (!file.isDirectory) {
    throw IOException("Expected directory instead of file: $path/$relativePath")
  }
  return file
}

@RequiresWriteLock
fun VirtualFile.findOrCreateFile(relativePath: @SystemIndependent String): VirtualFile {
  val directory = findOrCreateDirectory("$relativePath/..")
  val name = path.toNioPath().getResolvedPath(relativePath).name
  val file = directory.findChild(name) ?: directory.createChildData(fileSystem, name)
  if (!file.isFile) {
    throw IOException("Expected file instead of directory: $path/$relativePath")
  }
  return file
}

@RequiresWriteLock
fun VirtualFile.findOrCreateDirectory(relativePath: @SystemIndependent String): VirtualFile {
  var directory = checkNotNull(fileSystem.findFileByPath("/")) {
    "Cannot find file system root for file: $path/$relativePath"
  }
  val resolvedPath = path.toNioPath().getResolvedPath(relativePath)
  for (pathPart in resolvedPath) {
    val name = pathPart.pathString
    directory = directory.findChild(name) ?: directory.createChildDirectory(fileSystem, name)
    if (!directory.isDirectory) {
      throw IOException("Expected directory instead of file: ${directory.path}")
    }
  }
  return directory
}

fun Path.refreshAndFindVirtualFile(): VirtualFile? {
  val fileManager = VirtualFileManager.getInstance()
  val file = fileManager.refreshAndFindFileByNioPath(this) ?: return null
  if (!file.isFile) {
    throw IOException("Expected file instead of directory: $this")
  }
  return file
}

fun Path.refreshAndFindVirtualDirectory(): VirtualFile? {
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
