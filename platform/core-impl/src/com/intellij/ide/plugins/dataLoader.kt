// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.lang.ZipFilePool
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@ApiStatus.Internal
interface DataLoader {
  val pool: ZipFilePool?

  val emptyDescriptorIfCannotResolve: Boolean
    get() = false

  fun isExcludedFromSubSearch(jarFile: Path): Boolean = false

  fun load(path: String): ByteArray?

  override fun toString(): String
}

@ApiStatus.Internal
class LocalFsDataLoader(val basePath: Path) : DataLoader {
  override val pool: ZipFilePool?
    get() = ZipFilePool.POOL

  override val emptyDescriptorIfCannotResolve: Boolean
    get() = true

  override fun load(path: String): ByteArray? {
    return try {
      Files.readAllBytes(basePath.resolve(path))
    }
    catch (e: NoSuchFileException) {
      null
    }
  }

  override fun toString() = basePath.toString()
}