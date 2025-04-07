// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.ijent.nio

import org.jetbrains.annotations.ApiStatus
import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService

/**
 * See [IjentWslNioFileSystemProvider].
 */
@ApiStatus.Internal
class IjentWslNioFileSystem internal constructor(
  private val provider: IjentWslNioFileSystemProvider,
  internal val wslId: String,
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

  override fun getRootDirectories(): Iterable<Path> {
    // It is known that `originalFs.rootDirectories` always returns all WSL drives.
    // Also, it is known that `ijentFs.rootDirectories` returns a single WSL drive,
    // which is already mentioned in `originalFs.rootDirectories`.
    //
    // `ijentFs` is usually represented by `IjentFailSafeFileSystemPosixApi`,
    // which launches IJent and the corresponding WSL containers lazily.
    //
    // This function avoids fetching root directories directly from IJent.
    // This way, various UI file trees don't start all WSL containers during loading the file system root.
    return originalFs.rootDirectories
  }

  override fun getFileStores(): Iterable<FileStore> =
    originalFs.fileStores + ijentFs.fileStores

  override fun supportedFileAttributeViews(): Set<String> =
    LinkedHashSet<String>().apply {
      addAll(originalFs.supportedFileAttributeViews())
      addAll(ijentFs.supportedFileAttributeViews())
    }

  override fun getPath(first: String, vararg more: String): Path =
    IjentWslNioPath(this, originalFs.getPath(first, *more), null)

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher =
    originalFs.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
    originalFs.userPrincipalLookupService

  override fun newWatchService(): WatchService =
    originalFs.newWatchService()
}