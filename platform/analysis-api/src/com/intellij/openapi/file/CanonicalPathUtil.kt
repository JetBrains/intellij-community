// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.file

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Experimental
object CanonicalPathUtil {

  @JvmStatic
  fun String.toNioPath(): Path {
    return Paths.get(FileUtil.toSystemDependentName(this))
  }

  @JvmStatic
  fun String.toIoFile(): File {
    return toNioPath().toFile()
  }

  @JvmStatic
  fun String.getFileName(): String {
    return PathUtil.getFileName(this)
  }

  @JvmStatic
  fun String.getParentPath(): String? {
    return PathUtil.getParentPath(this).nullize()
  }

  @JvmStatic
  fun String.getParentNioPath(): Path? {
    return getParentPath()?.toNioPath()
  }

  @JvmStatic
  fun String.getAbsolutePath(relativePath: String): String {
    val path = "$this/$relativePath"
    return FileUtil.toCanonicalPath(path) // resolve simple symlinks . and ..
  }

  @JvmStatic
  fun String.getAbsoluteNioPath(relativePath: String): Path {
    return getAbsolutePath(relativePath).toNioPath()
  }

  @JvmStatic
  fun String.getRelativePath(path: String): String? {
    return FileUtil.getRelativePath(this, path, '/')
  }

  @JvmStatic
  fun String.getRelativeNioPath(path: String): Path? {
    return getRelativePath(path)?.toNioPath()
  }
}