// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio

import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService

/**
 * See [IjentWslNioFileSystemProvider].
 */
class IjentWslNioFileSystem(
  private val provider: IjentWslNioFileSystemProvider,
  private val wslId: String,
  private val ijentFs: FileSystem,
  private val originalFs: FileSystem,
) : FileSystem() {
  override fun toString(): String = """${javaClass.simpleName}($provider)"""

  override fun close() {
    provider.removeFileSystem(wslId)
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

  override fun getPath(first: String, vararg more: String): Path =
    IjentWslNioPath(this, originalFs.getPath(first, *more))

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher =
    originalFs.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
    originalFs.userPrincipalLookupService

  override fun newWatchService(): WatchService =
    originalFs.newWatchService()
}