// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.fileSystem

import org.jetbrains.annotations.ApiStatus
import java.io.File

@ApiStatus.Experimental
object IoFileSystemUtil {

  @JvmStatic
  fun findFileOrDirectory(file: File): File? {
    return NioFileSystemUtil.findFileOrDirectory(file.toPath())?.toFile()
  }

  @JvmStatic
  fun getFileOrDirectory(file: File): File {
    return NioFileSystemUtil.getFileOrDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun findFile(file: File): File? {
    return NioFileSystemUtil.findFile(file.toPath())?.toFile()
  }

  @JvmStatic
  fun getFile(file: File): File {
    return NioFileSystemUtil.getFile(file.toPath()).toFile()
  }

  @JvmStatic
  fun findDirectory(file: File): File? {
    return NioFileSystemUtil.findDirectory(file.toPath())?.toFile()
  }

  @JvmStatic
  fun getDirectory(file: File): File {
    return NioFileSystemUtil.getDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun findOrCreateFile(file: File): File {
    return NioFileSystemUtil.findOrCreateFile(file.toPath()).toFile()
  }

  @JvmStatic
  fun findOrCreateDirectory(file: File): File {
    return NioFileSystemUtil.findOrCreateDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun createFile(file: File): File {
    return NioFileSystemUtil.createFile(file.toPath()).toFile()
  }

  @JvmStatic
  fun createDirectory(file: File): File {
    return NioFileSystemUtil.createDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun deleteFileOrDirectory(file: File) {
    NioFileSystemUtil.deleteFileOrDirectory(file.toPath())
  }

  @JvmStatic
  fun deleteChildren(file: File, predicate: (File) -> Boolean = { true }) {
    NioFileSystemUtil.deleteChildren(file.toPath()) { predicate(it.toFile()) }
  }
}