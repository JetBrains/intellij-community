// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:JvmName("NioPathUtil")
package com.intellij.openapi.file

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createFile
import com.intellij.util.io.delete
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

fun Path.toCanonicalPath(): @SystemIndependent String {
  return FileUtil.toCanonicalPath(toString())
}

fun Path.toIoFile(): File {
  return toFile()
}

fun Path.getResolvedPath(relativePath: @SystemIndependent String): String {
  return toCanonicalPath().getResolvedPath(relativePath)
}

fun Path.getResolvedNioPath(relativePath: @SystemIndependent String): Path {
  return toCanonicalPath().getResolvedNioPath(relativePath)
}

fun Path.getRelativePath(path: Path): @SystemIndependent String? {
  return toCanonicalPath().getRelativePath(path.toCanonicalPath())
}

fun Path.getRelativeNioPath(path: Path): Path? {
  return toCanonicalPath().getRelativeNioPath(path.toCanonicalPath())
}

fun Path.isAncestor(path: Path, strict: Boolean): Boolean {
  return FileUtil.isAncestor(this, path, strict)
}

fun Path.createNioFile(): Path {
  return createFile()
}

fun Path.createNioDirectory(): Path {
  return createDirectories()
}

fun Path.findOrCreateNioFile(): Path {
  if (exists()) {
    check(isRegularFile()) {
      "Expected file instead of directory: $this"
    }
    return this
  }
  return createNioFile()
}

fun Path.findOrCreateNioDirectory(): Path {
  if (exists()) {
    check(isDirectory()) {
      "Expected directory instead of file: $this"
    }
    return this
  }
  return createNioDirectory()
}

fun Path.deleteNioFileOrDirectory() {
  delete(recursively = true)
}

fun Path.deleteNioChildren(predicate: (Path) -> Boolean = { true }) {
  val filter = DirectoryStream.Filter(predicate)
  Files.newDirectoryStream(this, filter).use { stream ->
    stream.forEach { it.deleteNioFileOrDirectory() }
  }
}
