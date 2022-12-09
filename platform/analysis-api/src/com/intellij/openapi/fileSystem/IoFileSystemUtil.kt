// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.fileSystem

import org.jetbrains.annotations.ApiStatus
import java.io.File

@ApiStatus.Experimental
object IoFileSystemUtil {

  @JvmStatic
  fun findFileOrDirectory(file: File): File? {
    return NioPathSystemUtil.findFileOrDirectory(file.toPath())?.toFile()
  }

  @JvmStatic
  fun getFileOrDirectory(file: File): File {
    return NioPathSystemUtil.getFileOrDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun findFile(file: File): File? {
    return NioPathSystemUtil.findFile(file.toPath())?.toFile()
  }

  @JvmStatic
  fun getFile(file: File): File {
    return NioPathSystemUtil.getFile(file.toPath()).toFile()
  }

  @JvmStatic
  fun findDirectory(file: File): File? {
    return NioPathSystemUtil.findDirectory(file.toPath())?.toFile()
  }

  @JvmStatic
  fun getDirectory(file: File): File {
    return NioPathSystemUtil.getDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun findOrCreateFile(file: File): File {
    return NioPathSystemUtil.findOrCreateFile(file.toPath()).toFile()
  }

  @JvmStatic
  fun findOrCreateDirectory(file: File): File {
    return NioPathSystemUtil.findOrCreateDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun createFile(file: File): File {
    return NioPathSystemUtil.createFile(file.toPath()).toFile()
  }

  @JvmStatic
  fun createDirectory(file: File): File {
    return NioPathSystemUtil.createDirectory(file.toPath()).toFile()
  }

  @JvmStatic
  fun deleteFileOrDirectory(file: File) {
    NioPathSystemUtil.deleteFileOrDirectory(file.toPath())
  }

  @JvmStatic
  fun deleteChildren(file: File, predicate: (File) -> Boolean = { true }) {
    NioPathSystemUtil.deleteChildren(file.toPath()) { predicate(it.toFile()) }
  }
}