// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@ApiStatus.Internal
interface DataLoader {
  val emptyDescriptorIfCannotResolve: Boolean
    get() = false

  fun isExcludedFromSubSearch(jarFile: Path): Boolean = false

  fun load(path: String, pluginDescriptorSourceOnly: Boolean): ByteArray?

  override fun toString(): String
}

@ApiStatus.Internal
class LocalFsDataLoader(@JvmField val basePath: Path) : DataLoader {
  override val emptyDescriptorIfCannotResolve: Boolean
    get() = true

  override fun load(path: String, pluginDescriptorSourceOnly: Boolean): ByteArray? {
    try {
      return Files.readAllBytes(basePath.resolve(path))
    }
    catch (_: NoSuchFileException) {
      return null
    }
  }

  override fun toString(): String = basePath.toString()
}