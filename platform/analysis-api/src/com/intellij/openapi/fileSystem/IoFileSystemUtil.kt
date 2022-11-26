// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.fileSystem

import java.io.File

object IoFileSystemUtil {

  fun findFileOrDirectory(file: File): File? {
    return NioFileSystemUtil.findFileOrDirectory(file.toPath())?.toFile()
  }

  fun getFileOrDirectory(file: File): File {
    return NioFileSystemUtil.getFileOrDirectory(file.toPath()).toFile()
  }

  fun findFile(file: File): File? {
    return NioFileSystemUtil.findFile(file.toPath())?.toFile()
  }

  fun getFile(file: File): File {
    return NioFileSystemUtil.getFile(file.toPath()).toFile()
  }

  fun findDirectory(file: File): File? {
    return NioFileSystemUtil.findDirectory(file.toPath())?.toFile()
  }

  fun getDirectory(file: File): File {
    return NioFileSystemUtil.getDirectory(file.toPath()).toFile()
  }

  fun findOrCreateFile(file: File): File {
    return NioFileSystemUtil.findOrCreateFile(file.toPath()).toFile()
  }

  fun findOrCreateDirectory(file: File): File {
    return NioFileSystemUtil.findOrCreateDirectory(file.toPath()).toFile()
  }

  fun createFile(file: File): File {
    return NioFileSystemUtil.createFile(file.toPath()).toFile()
  }

  fun createDirectory(file: File): File {
    return NioFileSystemUtil.createDirectory(file.toPath()).toFile()
  }

  fun deleteFileOrDirectory(file: File) {
    NioFileSystemUtil.deleteFileOrDirectory(file.toPath())
  }

  fun deleteChildren(file: File, predicate: (File) -> Boolean = { true }) {
    NioFileSystemUtil.deleteChildren(file.toPath()) { predicate(it.toFile()) }
  }
}