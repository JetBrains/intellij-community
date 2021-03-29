// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.ProviderNotFoundException
import java.nio.file.spi.FileSystemProvider
import java.util.*

// parentContext is null only for CoreApplicationEnvironment - it is not valid otherwise because in this case XML is not interned.
internal class DescriptorLoadingContext : AutoCloseable {
  private var openedFiles: MutableMap<Path, FileSystem>? = null

  private var zipFsProvider: FileSystemProvider? = null

  private fun getZipFsProvider(): FileSystemProvider {
    var result = zipFsProvider
    if (result == null) {
      result = findZipFsProvider()
      zipFsProvider = result
    }
    return result
  }

  private fun findZipFsProvider(): FileSystemProvider {
    for (provider in FileSystemProvider.installedProviders()) {
      try {
        if (provider.scheme == "jar") {
          return provider
        }
      }
      catch (ignored: UnsupportedOperationException) {
      }
    }
    throw ProviderNotFoundException("Provider not found")
  }

  fun open(file: Path): FileSystem {
    if (openedFiles == null) {
      openedFiles = HashMap()
    }
    return openedFiles!!.computeIfAbsent(file) {
      @Suppress("SpellCheckingInspection")
      getZipFsProvider().newFileSystem(it, Collections.singletonMap("zipinfo-time", "false"))
    }
  }

  override fun close() {
    for (file in (openedFiles ?: return).values) {
      try {
        file.close()
      }
      catch (ignore: IOException) {
      }
    }
  }
}