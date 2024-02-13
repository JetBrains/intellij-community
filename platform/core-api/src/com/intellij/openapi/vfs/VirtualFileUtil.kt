// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualFileUtil")

package com.intellij.openapi.vfs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.CanonicalPathPrefixTreeFactory
import com.intellij.openapi.util.io.relativizeToClosestAncestor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import org.jetbrains.annotations.SystemIndependent
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.pathString

fun VirtualFile.validOrNull() = if (isValid) this else null

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

private fun VirtualFile.relativizeToClosestAncestor(
  relativePath: String
): Pair<VirtualFile, Path> {
  val basePath = Path.of(path)
  val (normalizedBasePath, normalizedRelativePath) = basePath.relativizeToClosestAncestor(relativePath)
  var baseVirtualFile = this
  repeat(basePath.nameCount - normalizedBasePath.nameCount) {
    baseVirtualFile = checkNotNull(baseVirtualFile.parent) {
      """
        |Cannot resolve base virtual file for $baseVirtualFile
        |  basePath = $path
        |  relativePath = $relativePath
      """.trimMargin()
    }
  }
  return baseVirtualFile to normalizedRelativePath
}

private inline fun VirtualFile.getResolvedVirtualFile(
  relativePath: String,
  getChild: VirtualFile.(String, Boolean) -> VirtualFile
): VirtualFile {
  val (baseVirtualFile, normalizedRelativePath) = relativizeToClosestAncestor(relativePath)
  var virtualFile = baseVirtualFile
  if (normalizedRelativePath.pathString.isNotEmpty()) {
    val names = normalizedRelativePath.map { it.pathString }
    for ((i, name) in names.withIndex()) {
      if (!virtualFile.isDirectory) {
        throw IOException("""
          |Expected directory instead of file: $virtualFile
          |  basePath = $path
          |  relativePath = $relativePath
        """.trimMargin())
      }
      virtualFile = virtualFile.getChild(name, i == names.lastIndex)
    }
  }
  return virtualFile
}

@RequiresReadLock
fun VirtualFile.findFileOrDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  return getResolvedVirtualFile(relativePath) { name, _ ->
    findChild(name) ?: return null // return from findFileOrDirectory
  }
}

@RequiresReadLock
fun VirtualFile.findFile(relativePath: @SystemIndependent String): VirtualFile? {
  val file = findFileOrDirectory(relativePath) ?: return null
  if (!file.isFile) {
    throw IOException("""
      |Expected file instead of directory: $file
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return file
}

@RequiresReadLock
fun VirtualFile.findDirectory(relativePath: @SystemIndependent String): VirtualFile? {
  val directory = findFileOrDirectory(relativePath) ?: return null
  if (!directory.isDirectory) {
    throw IOException("""
      |Expected directory instead of file: $directory
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return directory
}

@RequiresWriteLock
fun VirtualFile.findOrCreateFile(relativePath: @SystemIndependent String): VirtualFile {
  val file = getResolvedVirtualFile(relativePath) { name, isLast ->
    findChild(name) ?: when (isLast) {
      true -> createChildData(fileSystem, name)
      else -> createChildDirectory(fileSystem, name)
    }
  }
  if (!file.isFile) {
    throw IOException("""
      |Expected file instead of directory: $file
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return file
}

@RequiresWriteLock
fun VirtualFile.findOrCreateDirectory(relativePath: @SystemIndependent String): VirtualFile {
  val directory = getResolvedVirtualFile(relativePath) { name, _ ->
    findChild(name) ?: createChildDirectory(fileSystem, name)
  }
  if (!directory.isDirectory) {
    throw IOException("""
      |Expected directory instead of file: $directory
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return directory
}

fun Path.refreshAndFindVirtualFileOrDirectory(): VirtualFile? {
  val fileManager = VirtualFileManager.getInstance()
  return fileManager.refreshAndFindFileByNioPath(this)
}

fun Path.refreshAndFindVirtualFile(): VirtualFile? {
  val file = refreshAndFindVirtualFileOrDirectory() ?: return null
  if (!file.isFile) {
    throw IOException("Expected file instead of directory: $this")
  }
  return file
}

fun Path.refreshAndFindVirtualDirectory(): VirtualFile? {
  val file = refreshAndFindVirtualFileOrDirectory() ?: return null
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

/**
 * Resolves [VirtualFile] from absolute [absoluteOrRelativeFilePath] if found or by relative [absoluteOrRelativeFilePath] from [VirtualFile]
 */
fun VirtualFile.resolveFromRootOrRelative(absoluteOrRelativeFilePath: String): VirtualFile? {
  return fileSystem.findFileByPath(absoluteOrRelativeFilePath) ?: findFileByRelativePath(absoluteOrRelativeFilePath)
}
