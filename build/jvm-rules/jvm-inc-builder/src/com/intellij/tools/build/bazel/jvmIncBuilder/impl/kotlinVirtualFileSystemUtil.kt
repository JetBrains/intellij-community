// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink
import com.intellij.util.PathUtilRt
import org.jetbrains.kotlin.load.kotlin.LibraryContainerAwareVirtualFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.Collection

/*
 * This file is a copy of community/build/jvm-rules/src/kotlin/kotlin-builder/OutputVirtualFile.kt but with a custom KotlinVirtualFileProvider
 */

internal class OutputFileSystem(
  @JvmField val kotlinVirtualFileProvider: KotlinVirtualFileProvider,
) : DeprecatedVirtualFileSystem() {
  @JvmField val root = OutputVirtualFile(
    fs = this,
    name = "",
    relativePath = "",
    parent = null,
    isDir = true,
  )

  override fun getProtocol(): String = StandardFileSystems.JAR_PROTOCOL

  override fun findFileByPath(path: String): VirtualFile? {
    val separator = path.indexOf("!/")
    require(separator >= 0) { "Path in JarFileSystem must contain a separator: $path" }
    //val localPath = path.substring(0, separator)
    val pathInJar = path.substring(separator + 2)
    return root.findFileByRelativePath(pathInJar)
  }

  override fun refresh(asynchronous: Boolean) {
  }

  override fun refreshAndFindFileByPath(path: String): VirtualFile? {
    return findFileByPath(path)
  }
}

private class PathAdapter(@JvmField val vf: OutputVirtualFile) : Path {
  override fun getFileSystem(): FileSystem? {
    TODO("Not yet implemented")
  }

  override fun isAbsolute(): Boolean = true

  override fun getRoot(): Path? {
    TODO("Not yet implemented")
  }

  override fun getFileName(): Path? {
    TODO("Not yet implemented")
  }

  override fun getParent(): Path? {
    TODO("Not yet implemented")
  }

  override fun getNameCount(): Int {
    TODO("Not yet implemented")
  }

  override fun getName(index: Int): Path? {
    TODO("Not yet implemented")
  }

  override fun subpath(beginIndex: Int, endIndex: Int): Path? {
    TODO("Not yet implemented")
  }

  override fun startsWith(other: Path?): Boolean {
    if (other !is PathAdapter) {
      return false
    }
    return other == this || other.vf.relativePath.startsWith(vf.relativePath)
  }

  override fun endsWith(other: Path?): Boolean {
    TODO("Not yet implemented")
  }

  override fun normalize(): Path = this

  override fun resolve(other: Path?): Path? {
    TODO("Not yet implemented")
  }

  override fun relativize(other: Path?): Path? {
    TODO("Not yet implemented")
  }

  override fun toUri(): URI? {
    TODO("Not yet implemented")
  }

  override fun toAbsolutePath(): Path = this

  override fun toRealPath(vararg options: LinkOption?): Path = this

  override fun register(watcher: WatchService?, events: Array<out WatchEvent.Kind<*>?>?, vararg modifiers: WatchEvent.Modifier?): WatchKey? {
    return null
  }

  override fun compareTo(other: Path): Int {
    return if (other == this) 0 else -1
  }
}

internal class OutputVirtualFile(
  private val fs: OutputFileSystem,
  private val name: String,
  @JvmField val relativePath: String,
  private val parent: OutputVirtualFile?,
  private val isDir: Boolean,
) : VirtualFile(), LibraryContainerAwareVirtualFile {
  private val cachedChildren by lazy {
    val result = mutableListOf<OutputVirtualFile>()

    fs.kotlinVirtualFileProvider.findVfsChildren(relativePath, {
      result.add(OutputVirtualFile(
        fs = fs,
        name = it,
        parent = this,
        isDir = true,
        relativePath = if (relativePath.isEmpty()) it else "$relativePath/$it",
      ))
    }) { relativePath ->
      result.add(OutputVirtualFile(
        fs = fs,
        name = PathUtilRt.getFileName(relativePath),
        parent = this,
        isDir = false,
        relativePath = relativePath,
      ))
    }

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    (result as Collection<OutputVirtualFile>).toArray(EMPTY_ARRAY) as Array<VirtualFile>
  }

  private val pathAdapter by lazy {
    PathAdapter(this)
  }

  override fun getContainingLibraryPath(): Path {
    return fs.root.pathAdapter
  }

  override fun toNioPath(): Path {
    return pathAdapter
  }

  override fun getName(): String {
    return name
  }

  override fun getNameSequence(): CharSequence {
    return name
  }

  override fun getFileSystem(): VirtualFileSystem {
    return fs
  }

  override fun getPath(): String {
    if (parent == null) {
      return "__module_in-memory__output__!/"
    }

    val parentPath = parent.path
    val answer = StringBuilder(parentPath.length + 1 + name.length)
    answer.append(parentPath)
    if (answer[answer.length - 1] != '/') {
      answer.append('/')
    }
    answer.append(name)
    return answer.toString()
  }

  override fun isWritable(): Boolean {
    return false
  }

  override fun isDirectory(): Boolean {
    return isDir
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun getParent(): VirtualFile? {
    return parent
  }

  override fun getChildren(): Array<VirtualFile> {
    if (!isDir) {
      return EMPTY_ARRAY
    }
    return cachedChildren
  }

  override fun getOutputStream(requestor: Any, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
    throw UnsupportedOperationException("JarFileSystem is read-only")
  }

  @Throws(IOException::class)
  override fun contentsToByteArray(): ByteArray {
    if (isDir) {
      return EMPTY_BYTE_ARRAY
    }
    val data = fs.kotlinVirtualFileProvider.getData(relativePath)
    if (data == null) {
      return EMPTY_BYTE_ARRAY
    }
    return data
  }

  override fun isInLocalFileSystem(): Boolean {
    return false
  }

  override fun getTimeStamp(): Long = 0

  override fun getLength(): Long = if (isDir) -1 else fs.kotlinVirtualFileProvider.getSize(relativePath).toLong()

  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

  override fun getInputStream(): InputStream {
    return BufferExposingByteArrayInputStream(contentsToByteArray())
  }

  override fun getModificationStamp(): Long = 0
}

private val EMPTY_BYTE_ARRAY = ByteArray(0)