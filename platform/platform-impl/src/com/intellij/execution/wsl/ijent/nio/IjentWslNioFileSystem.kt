// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio

import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import java.nio.file.*

/**
 * See [IjentWslNioFileSystemProvider].
 */
internal class IjentWslNioFileSystem(
  private val provider: IjentWslNioFileSystemProvider,
  private val ijentFs: FileSystem,
  private val originalFs: FileSystem,
) : DelegatingFileSystem<IjentWslNioFileSystemProvider>() {
  override fun toString(): String = """${javaClass.simpleName}(ijentId=${provider.ijentId}, wslLocalRoot=${provider.wslLocalRoot})"""

  override fun getDelegate(): FileSystem = originalFs

  override fun getDelegate(root: String): FileSystem = originalFs

  override fun close() {
    ijentFs.close()
  }

  override fun provider(): IjentWslNioFileSystemProvider = provider

  override fun isOpen(): Boolean = true

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String = originalFs.separator

  override fun getRootDirectories(): Iterable<Path> =
    LinkedHashSet<Path>().apply {
      addAll(originalFs.rootDirectories)
      addAll(ijentFs.rootDirectories)
    }

  override fun getFileStores(): Iterable<FileStore> =
    originalFs.fileStores + ijentFs.fileStores

  override fun supportedFileAttributeViews(): Set<String> =
    LinkedHashSet<String>().apply {
      addAll(originalFs.supportedFileAttributeViews())
      addAll(ijentFs.supportedFileAttributeViews())
    }
}