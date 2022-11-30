// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package com.intellij.openapi.file

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.text.nullize
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object CanonicalPathUtil {

  fun String.toNioPath(): Path {
    return Paths.get(FileUtil.toSystemDependentName(this))
  }

  fun String.toIoFile(): File {
    return toNioPath().toFile()
  }

  fun String.getFileName(): String {
    return PathUtil.getFileName(this)
  }

  fun String.getParentPath(): String? {
    return PathUtil.getParentPath(this).nullize()
  }

  fun String.getParentNioPath(): Path? {
    return getParentPath()?.toNioPath()
  }

  fun String.getAbsolutePath(relativePath: String): String {
    val path = "$this/$relativePath"
    return FileUtil.toCanonicalPath(path) // resolve simple symlinks . and ..
  }

  fun String.getAbsoluteNioPath(relativePath: String): Path {
    return getAbsolutePath(relativePath).toNioPath()
  }

  fun String.getRelativePath(path: String): String? {
    return FileUtil.getRelativePath(this, path, '/')
  }

  fun String.getRelativeNioPath(path: String): Path? {
    return getRelativePath(path)?.toNioPath()
  }
}