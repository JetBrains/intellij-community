// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.file

import com.intellij.openapi.file.NioFileUtil.toCanonicalPath
import java.io.File
import java.nio.file.Path

object IoFileUtil {

  fun exists(file: File): Boolean {
    return file.exists()
  }

  fun isFile(file: File): Boolean {
    return file.isFile
  }

  fun isDirectory(file: File): Boolean {
    return file.isDirectory
  }

  fun getTextContent(file: File): String {
    return file.readText()
  }

  fun setTextContent(file: File, text: String) {
    file.writeText(text)
  }

  fun getBinaryContent(file: File): ByteArray {
    return file.inputStream().use { it.readBytes() }
  }

  fun setBinaryContent(file: File, bytes: ByteArray) {
    file.outputStream().use { it.write(bytes) }
  }

  fun findFileOrDirectory(file: File, relativePath: String): File? {
    return NioFileUtil.findFileOrDirectory(file.toNioPath(), relativePath)?.toFile()
  }

  fun getFileOrDirectory(file: File, relativePath: String): File {
    return NioFileUtil.getFileOrDirectory(file.toNioPath(), relativePath).toFile()
  }

  fun findFile(file: File, relativePath: String): File? {
    return NioFileUtil.findFile(file.toNioPath(), relativePath)?.toFile()
  }

  fun getFile(file: File, relativePath: String): File {
    return NioFileUtil.getFile(file.toNioPath(), relativePath).toFile()
  }

  fun findDirectory(file: File, relativePath: String): File? {
    return NioFileUtil.findDirectory(file.toNioPath(), relativePath)?.toFile()
  }

  fun getDirectory(file: File, relativePath: String): File {
    return NioFileUtil.getDirectory(file.toNioPath(), relativePath).toFile()
  }

  fun findOrCreateFile(file: File, relativePath: String): File {
    return NioFileUtil.findOrCreateFile(file.toNioPath(), relativePath).toFile()
  }

  fun findOrCreateDirectory(file: File, relativePath: String): File {
    return NioFileUtil.findOrCreateDirectory(file.toNioPath(), relativePath).toFile()
  }

  fun createFile(file: File, relativePath: String): File {
    return NioFileUtil.createFile(file.toNioPath(), relativePath).toFile()
  }

  fun createDirectory(file: File, relativePath: String): File {
    return NioFileUtil.createDirectory(file.toNioPath(), relativePath).toFile()
  }

  fun deleteFileOrDirectory(file: File, relativePath: String = ".") {
    NioFileUtil.deleteFileOrDirectory(file.toNioPath(), relativePath)
  }

  fun deleteChildren(file: File, relativePath: String = ".", predicate: (File) -> Boolean = { true }) {
    NioFileUtil.deleteChildren(file.toNioPath(), relativePath) { predicate(it.toFile()) }
  }

  fun File.toCanonicalPath(): String {
    return toNioPath().toCanonicalPath()
  }

  fun File.toNioPath(): Path {
    return toPath()
  }
}