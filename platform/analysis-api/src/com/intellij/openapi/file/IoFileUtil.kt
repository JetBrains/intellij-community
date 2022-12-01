// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.file

import com.intellij.openapi.file.NioFileUtil.toCanonicalPath
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path

@ApiStatus.Experimental
object IoFileUtil {

  @JvmStatic
  fun exists(file: File): Boolean {
    return file.exists()
  }

  @JvmStatic
  fun isFile(file: File): Boolean {
    return file.isFile
  }

  @JvmStatic
  fun isDirectory(file: File): Boolean {
    return file.isDirectory
  }

  @JvmStatic
  fun getTextContent(file: File): String {
    return file.readText()
  }

  @JvmStatic
  fun setTextContent(file: File, text: String) {
    file.writeText(text)
  }

  @JvmStatic
  fun getBinaryContent(file: File): ByteArray {
    return file.inputStream().use { it.readBytes() }
  }

  @JvmStatic
  fun setBinaryContent(file: File, bytes: ByteArray) {
    file.outputStream().use { it.write(bytes) }
  }

  @JvmStatic
  fun findFileOrDirectory(file: File, relativePath: String): File? {
    return NioFileUtil.findFileOrDirectory(file.toNioPath(), relativePath)?.toFile()
  }

  @JvmStatic
  fun getFileOrDirectory(file: File, relativePath: String): File {
    return NioFileUtil.getFileOrDirectory(file.toNioPath(), relativePath).toFile()
  }

  @JvmStatic
  fun findFile(file: File, relativePath: String): File? {
    return NioFileUtil.findFile(file.toNioPath(), relativePath)?.toFile()
  }

  @JvmStatic
  fun getFile(file: File, relativePath: String): File {
    return NioFileUtil.getFile(file.toNioPath(), relativePath).toFile()
  }

  @JvmStatic
  fun findDirectory(file: File, relativePath: String): File? {
    return NioFileUtil.findDirectory(file.toNioPath(), relativePath)?.toFile()
  }

  @JvmStatic
  fun getDirectory(file: File, relativePath: String): File {
    return NioFileUtil.getDirectory(file.toNioPath(), relativePath).toFile()
  }

  @JvmStatic
  fun findOrCreateFile(file: File, relativePath: String): File {
    return NioFileUtil.findOrCreateFile(file.toNioPath(), relativePath).toFile()
  }

  @JvmStatic
  fun findOrCreateDirectory(file: File, relativePath: String): File {
    return NioFileUtil.findOrCreateDirectory(file.toNioPath(), relativePath).toFile()
  }

  @JvmStatic
  fun createFile(file: File, relativePath: String): File {
    return NioFileUtil.createFile(file.toNioPath(), relativePath).toFile()
  }

  @JvmStatic
  fun createDirectory(file: File, relativePath: String): File {
    return NioFileUtil.createDirectory(file.toNioPath(), relativePath).toFile()
  }

  @JvmStatic
  fun deleteFileOrDirectory(file: File, relativePath: String = ".") {
    NioFileUtil.deleteFileOrDirectory(file.toNioPath(), relativePath)
  }

  @JvmStatic
  fun deleteChildren(file: File, relativePath: String = ".", predicate: (File) -> Boolean = { true }) {
    NioFileUtil.deleteChildren(file.toNioPath(), relativePath) { predicate(it.toFile()) }
  }

  @JvmStatic
  fun File.toCanonicalPath(): String {
    return toNioPath().toCanonicalPath()
  }

  @JvmStatic
  fun File.toNioPath(): Path {
    return toPath()
  }
}