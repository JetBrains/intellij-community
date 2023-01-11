// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:JvmName("CanonicalPathUtil")
package com.intellij.openapi.file

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.text.nullize
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


fun @SystemIndependent String.toNioPath(): Path {
  return Paths.get(FileUtil.toSystemDependentName(this))
}

fun @SystemIndependent String.toIoFile(): File {
  return toNioPath().toFile()
}

fun @SystemIndependent String.getFileName(): String {
  return PathUtil.getFileName(this)
}

fun @SystemIndependent String.getParentPath(): @SystemIndependent String? {
  return PathUtil.getParentPath(this).nullize()
}

fun @SystemIndependent String.getParentNioPath(): Path? {
  return getParentPath()?.toNioPath()
}

fun @SystemIndependent String.getResolvedPath(relativePath: @SystemIndependent String): @SystemIndependent String {
  val path = "$this/$relativePath"
  return FileUtil.toCanonicalPath(path) // resolve simple symlinks . and ..
}

fun @SystemIndependent String.getResolvedNioPath(relativePath: @SystemIndependent String): Path {
  return getResolvedPath(relativePath).toNioPath()
}

fun @SystemIndependent String.getRelativePath(path: @SystemIndependent String): @SystemIndependent String? {
  return FileUtil.getRelativePath(this, path, '/')
}

fun @SystemIndependent String.getRelativeNioPath(path: @SystemIndependent String): Path? {
  return getRelativePath(path)?.toNioPath()
}

fun @SystemIndependent String.isAncestor(path: @SystemIndependent String, strict: Boolean): Boolean {
  return FileUtil.isAncestor(this, path, strict)
}
