// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualFileUtil")

package com.intellij.openapi.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.CanonicalPathPrefixTreeFactory
import com.intellij.openapi.util.io.relativizeToClosestAncestor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.SystemIndependent
import java.io.IOException
import java.lang.ref.SoftReference
import java.nio.file.Path
import java.nio.file.Paths
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

/**
 * @return [LightVirtualFileBase.originalFile]
 */
fun VirtualFile.originalFile(): VirtualFile? {
  return this.asSafely<LightVirtualFileBase>()?.originalFile
}

/**
 * @return [LightVirtualFileBase.originalFile] or self
 */
fun VirtualFile.originalFileOrSelf(): VirtualFile {
  return originalFile() ?: this
}

private fun VirtualFile.relativizeToClosestAncestor(
  relativePath: String
): Pair<VirtualFile, Path> {
  val basePath = Paths.get(path)
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

@ApiStatus.Internal
object VirtualFilePrefixTreeFactory : AbstractPrefixTreeFactory<VirtualFile, String>() {

  override fun convertToList(element: VirtualFile): List<String> {
    return CanonicalPathPrefixTreeFactory.convertToList(element.path)
  }
}

/**
 * Resolves [VirtualFile] from absolute [absoluteOrRelativeFilePath] if found or by relative [absoluteOrRelativeFilePath] from [VirtualFile]
 */
@RequiresBackgroundThread(generateAssertion = false)
fun VirtualFile.resolveFromRootOrRelative(absoluteOrRelativeFilePath: String): VirtualFile? {
  return fileSystem.findFileByPath(absoluteOrRelativeFilePath) ?: findFileByRelativePath(absoluteOrRelativeFilePath)
}

/**
 * An alternative to `CachedValuesManager` for [VirtualFile].
 * It should be used when the cached value is dependent only on current file contents.
 *
 * @param key            Key, under which the cached value is going to be stored
 * @param provider       Cached value provider. The result should depend only on the contents of the file
 * @param useSoftCache   Whether to use [SoftReference] for storing the cached value
 * @param canCache       Whether the value can be cached in particular circumstance
 */
@Experimental
fun <T : Any> VirtualFile.getCachedValue(key: Key<VirtualFileCachedValue<T>>,
                                         useSoftCache: Boolean = false,
                                         canCache: ((VirtualFile) -> Boolean)? = null,
                                         provider: (VirtualFile, CharSequence?) -> T,): T {
  if (!isValid() && ApplicationManager.getApplication().isReadAccessAllowed) {
    thisLogger().error(InvalidVirtualFileAccessException(this))
    return provider(this, null)
  }
  ProgressManager.checkCanceled()
  val document = FileDocumentManager.getInstance().getCachedDocument(this)
  var cached = key.get(this)
  val documentModificationStamp = document?.modificationStamp ?: -1
  var data = cached?.data
  if (cached == null
      || data == null
      || cached.documentModificationStamp != documentModificationStamp
      || cached.fileModificationStamp != modificationStamp) {
    val text = loadText(this, document)
    data = provider(this, text)
    cached = VirtualFileCachedValue(data, useSoftCache, modificationStamp, documentModificationStamp)
    if (canCache?.invoke(this) != false) {
      key.set(this, cached)
    }
  }
  return data
}

private fun loadText(packageJsonFile: VirtualFile, packageJsonDocument: Document?): CharSequence? {
  if (packageJsonDocument != null) {
    return packageJsonDocument.immutableCharSequence
  }
  return try {
    VfsUtilCore.loadText(packageJsonFile)
  }
  catch (e: IOException) {
    null
  }
}

@Experimental
class VirtualFileCachedValue<T> private constructor(
  private val strongData: T?,
  private val weakData: SoftReference<T?>?,
  internal val fileModificationStamp: Long,
  internal val documentModificationStamp: Long,
  ) {

  internal constructor(data: T, useWeakCache: Boolean, fileModificationStamp: Long, documentModificationStamp: Long, ):
    this(if (useWeakCache) null else data,
         if (useWeakCache) SoftReference(data) else null,
         fileModificationStamp, documentModificationStamp)

  internal val data: T? get() = strongData ?: weakData?.get()

  override fun toString(): String {
    return "data=" + data +
           ", fileModificationStamp=" + fileModificationStamp +
           ", documentModificationStamp=" + documentModificationStamp
  }
}