// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.file.system

import com.intellij.util.io.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

@ApiStatus.Experimental
object NioPathSystemUtil {

  @JvmStatic
  fun findFileOrDirectory(path: Path): Path? {
    if (!path.exists()) {
      return null
    }
    return path
  }

  @JvmStatic
  fun getFileOrDirectory(path: Path): Path {
    return requireNotNull(findFileOrDirectory(path)) { "File or directory doesn't exist: $path" }
  }

  @JvmStatic
  fun findFile(path: Path): Path? {
    val filePath = findFileOrDirectory(path) ?: return null
    require(filePath.isRegularFile()) { "Expected file instead directory: $filePath" }
    return path
  }

  @JvmStatic
  fun getFile(path: Path): Path {
    return requireNotNull(findFile(path)) { "File doesn't exist: $path" }
  }

  @JvmStatic
  fun findDirectory(path: Path): Path? {
    val filePath = findFileOrDirectory(path) ?: return null
    require(filePath.isDirectory()) { "Expected directory instead file: $filePath" }
    return filePath
  }

  @JvmStatic
  fun getDirectory(path: Path): Path {
    return requireNotNull(findDirectory(path)) { "Directory doesn't exist: $path" }
  }

  @JvmStatic
  fun findOrCreateFile(path: Path): Path {
    return findFile(path) ?: createFile(path)
  }

  @JvmStatic
  fun findOrCreateDirectory(path: Path): Path {
    return findDirectory(path) ?: createDirectory(path)
  }

  @JvmStatic
  fun createFile(path: Path): Path {
    return path.createFile()
  }

  @JvmStatic
  fun createDirectory(path: Path): Path {
    return path.createDirectories()
  }

  @JvmStatic
  fun deleteFileOrDirectory(path: Path) {
    path.delete(recursively = true)
  }

  @JvmStatic
  fun deleteChildren(path: Path, predicate: (Path) -> Boolean = { true }) {
    val filter = DirectoryStream.Filter(predicate)
    Files.newDirectoryStream(path, filter).use { stream ->
      stream.forEach { deleteFileOrDirectory(it) }
    }
  }
}