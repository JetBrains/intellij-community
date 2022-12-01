// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.intellij.openapi.file

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.file.CanonicalPathUtil.getAbsoluteNioPath
import com.intellij.openapi.file.CanonicalPathUtil.getAbsolutePath
import com.intellij.openapi.file.CanonicalPathUtil.getRelativeNioPath
import com.intellij.openapi.file.CanonicalPathUtil.getRelativePath
import com.intellij.openapi.fileSystem.NioFileSystemUtil
import com.intellij.util.io.*
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

@ApiStatus.Experimental
object NioFileUtil {

  @JvmStatic
  fun exists(path: Path): Boolean {
    return path.exists()
  }

  @JvmStatic
  fun isFile(path: Path): Boolean {
    return path.isFile()
  }

  @JvmStatic
  fun isDirectory(path: Path): Boolean {
    return path.isDirectory()
  }

  @JvmStatic
  fun getTextContent(path: Path): String {
    return path.readText()
  }

  @JvmStatic
  fun setTextContent(path: Path, text: String) {
    path.writeText(text)
  }

  @JvmStatic
  fun getBinaryContent(path: Path): ByteArray {
    return path.inputStream().use { it.readBytes() }
  }

  @JvmStatic
  fun setBinaryContent(path: Path, bytes: ByteArray) {
    path.outputStream().use { it.write(bytes) }
  }

  @JvmStatic
  fun findFileOrDirectory(path: Path, relativePath: String): Path? {
    return NioFileSystemUtil.findFileOrDirectory(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun getFileOrDirectory(path: Path, relativePath: String): Path {
    return NioFileSystemUtil.getFileOrDirectory(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun findFile(path: Path, relativePath: String): Path? {
    return NioFileSystemUtil.findFile(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun getFile(path: Path, relativePath: String): Path {
    return NioFileSystemUtil.getFile(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun findDirectory(path: Path, relativePath: String): Path? {
    return NioFileSystemUtil.findDirectory(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun getDirectory(path: Path, relativePath: String): Path {
    return NioFileSystemUtil.getDirectory(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun findOrCreateFile(path: Path, relativePath: String): Path {
    return NioFileSystemUtil.findOrCreateFile(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun findOrCreateDirectory(path: Path, relativePath: String): Path {
    return NioFileSystemUtil.findOrCreateDirectory(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun createFile(path: Path, relativePath: String): Path {
    return NioFileSystemUtil.createFile(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun createDirectory(path: Path, relativePath: String): Path {
    return NioFileSystemUtil.createDirectory(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun deleteFileOrDirectory(path: Path, relativePath: String = ".") {
    NioFileSystemUtil.deleteFileOrDirectory(path.getAbsoluteNioPath(relativePath))
  }

  @JvmStatic
  fun deleteChildren(path: Path, relativePath: String = ".", predicate: (Path) -> Boolean = { true }) {
    NioFileSystemUtil.deleteChildren(path.getAbsoluteNioPath(relativePath), predicate)
  }

  @JvmStatic
  fun Path.toCanonicalPath(): String {
    return FileUtil.toCanonicalPath(toString())
  }

  @JvmStatic
  fun Path.toIoFile(): File {
    return toFile()
  }

  @JvmStatic
  fun Path.getAbsolutePath(relativePath: String): String {
    return toCanonicalPath().getAbsolutePath(relativePath)
  }

  @JvmStatic
  fun Path.getAbsoluteNioPath(relativePath: String): Path {
    return toCanonicalPath().getAbsoluteNioPath(relativePath)
  }

  @JvmStatic
  fun Path.getRelativePath(path: Path): String? {
    return toCanonicalPath().getRelativePath(path.toCanonicalPath())
  }

  @JvmStatic
  fun Path.getRelativeNioPath(path: Path): Path? {
    return toCanonicalPath().getRelativeNioPath(path.toCanonicalPath())
  }
}