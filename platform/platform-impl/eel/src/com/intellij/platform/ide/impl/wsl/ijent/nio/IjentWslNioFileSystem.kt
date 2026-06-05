// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.ijent.nio

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.provider.utils.impl.ijentToLocal
import com.intellij.platform.eel.provider.EelDescriptorOwner
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService

/**
 * See [IjentWslNioFileSystemProvider].
 */
internal class IjentWslNioFileSystem internal constructor(
  private val provider: IjentWslNioFileSystemProvider,
  internal val wslId: String,
  private val ijentFs: FileSystem,
  private val originalFs: FileSystem,
  override val eelDescriptor: EelDescriptor,
) : FileSystem(), EelDescriptorOwner {
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

  @OptIn(EelDelicateApi::class)
  override fun getPath(first: String, vararg more: String): Path =
    IjentWslNioPath(this, originalFs.getPath(ijentToLocal(first), *more.map { ijentToLocal(it) }.toTypedArray()), null)

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher =
    originalFs.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
    originalFs.userPrincipalLookupService

  override fun newWatchService(): WatchService =
    ijentFs.newWatchService()

  override fun equals(other: Any?): Boolean =
    this === other ||
    other is IjentWslNioFileSystem &&
    provider == other.provider &&
    wslId == other.wslId

  override fun hashCode(): Int {
    var result = provider.hashCode()
    result = 31 * result + wslId.hashCode()
    return result
  }
}