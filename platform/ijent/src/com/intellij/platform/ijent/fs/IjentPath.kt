// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.ijent.IjentId
import org.jetbrains.annotations.ApiStatus

/**
 * This interface deliberately mimics API of [java.nio.file.Path].
 *
 * On Unix, the first element of an absolute path is always '/'.
 * On Windows, the root may contain several '\'.
 *
 * It consists of all methods of nio.Path which don't require any I/O.
 *
 * There were no plan to have any other implementation except [IjentPathImpl]. They are split only to ease code reading.
 */
@ApiStatus.Experimental
interface IjentPath : Comparable<IjentPath>, Iterable<IjentPath> {
  companion object {
    @JvmStatic
    fun parse(ijentId: IjentId, isWindows: Boolean, raw: String): IjentPath =
      IjentPathImpl.parse(ijentId, isWindows, raw)
  }

  val ijentId: IjentId

  val isWindows: Boolean

  /** See [java.nio.file.Path.isAbsolute] */
  val isAbsolute: Boolean

  /** See [java.nio.file.Path.getRoot] */
  val root: IjentPath?

  /** See [java.nio.file.Path.getFileName] */
  val fileName: IjentPath?

  /** See [java.nio.file.Path.getParent] */
  val parent: IjentPath?

  /** See [java.nio.file.Path.getNameCount] */
  val nameCount: Int

  /** See [java.nio.file.Path.getName] */
  fun getName(index: Int): IjentPath

  /** See [java.nio.file.Path.subpath] */
  fun subpath(beginIndex: Int, endIndex: Int): IjentPath

  /** See [java.nio.file.Path.startsWith] */
  fun startsWith(other: IjentPath): Boolean

  /** See [java.nio.file.Path.startsWith] */
  fun startsWith(other: String): Boolean

  /** See [java.nio.file.Path.endsWith] */
  fun endsWith(other: IjentPath): Boolean

  /** See [java.nio.file.Path.endsWith] */
  fun endsWith(other: String): Boolean

  /** See [java.nio.file.Path.normalize] */
  fun normalize(): IjentPath

  /** See [java.nio.file.Path.resolve] */
  fun resolve(other: IjentPath): IjentPath

  /** See [java.nio.file.Path.resolve] */
  fun resolve(other: String): IjentPath

  /** See [java.nio.file.Path.resolveSibling] */
  fun resolveSibling(other: IjentPath): IjentPath =
    parent?.resolve(other) ?: other

  /** See [java.nio.file.Path.resolveSibling] */
  fun resolveSibling(other: String): IjentPath

  /** See [java.nio.file.Path.relativize] */
  fun relativize(other: IjentPath): IjentPath {
    TODO("Check if it ever possible to do that without any access to FS")
  }

  /** See [java.nio.file.Path.iterator] */
  override fun iterator(): Iterator<IjentPath>

  /** See [java.nio.file.Path.toString] */
  override fun toString(): String

  /** TODO Describe the difference with [toString] */
  fun toDebugString(): String = toString()
}


//fun main() {
//  if ("windows" in System.getProperty("os.name").lowercase()) {
//    // C:\
//    println(java.nio.file.Path.of("C:\\Users").root)
//
//    // null
//    println(java.nio.file.Path.of("C:\\").parent)
//
//    // \\wsl.localhost\Ubuntu\
//    println(java.nio.file.Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user").root)
//
//    // null
//    println(java.nio.file.Path.of("\\\\wsl.localhost\\Ubuntu").parent)
//
//    // java.nio.file.InvalidPathException: UNC path is missing sharename: \\wsl.localhost
//    println(java.nio.file.Path.of("\\\\wsl.localhost").parent)
//  }
//  else {
//    // /
//    println(java.nio.file.Path.of("/tmp").root)
//
//    // /
//    println(java.nio.file.Path.of("/").root)
//
//    // null
//    println(java.nio.file.Path.of("/").parent)
//  }
//
//  // null
//  println(java.nio.file.Path.of("").parent)
//
//  // null
//  println(java.nio.file.Path.of("").root)
//
//  // ..
//  println(java.nio.file.Path.of("abc").relativize(java.nio.file.Path.of("")))
//
//  // def
//  println(java.nio.file.Path.of("").relativize(java.nio.file.Path.of("def")))
//}