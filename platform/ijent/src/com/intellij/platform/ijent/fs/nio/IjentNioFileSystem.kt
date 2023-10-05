// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs.nio

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentPath
import com.intellij.platform.ijent.fs.IjentPathImpl
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

internal class IjentNioFileSystem(
  private val fsProvider: IjentNioFileSystemProvider,
  internal val ijentFsApi: IjentFileSystemApi,
) : FileSystem() {
  override fun close() {
    // TODO("Not yet implemented")
  }

  override fun provider(): FileSystemProvider = fsProvider

  override fun isOpen(): Boolean =
    ijentFsApi.coroutineScope.isActive

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String =
    if (ijentFsApi.isWindows) "\\" else "/"

  override fun getRootDirectories(): Iterable<Path> = fsBlocking {
    ijentFsApi.getRootDirectories().map { it.toNioPath() }
  }

  override fun getFileStores(): Iterable<FileStore> =
    listOf(IjentNioFileStore(ijentFsApi))

  override fun supportedFileAttributeViews(): Set<String> {
    TODO("Not yet implemented")
  }

  override fun getPath(first: String, vararg more: String): Path =
    more
      .fold(
        IjentPathImpl.parse(ijentFsApi.id, ijentFsApi.isWindows, first),
        IjentPath::resolve,
      )
      .toNioPath()

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher {
    TODO("Not yet implemented")
  }

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
    TODO("Not yet implemented")
  }

  override fun newWatchService(): WatchService {
    TODO("Not yet implemented")
  }

  // TODO runBlockingCancellable?
  internal fun <T> fsBlocking(body: suspend () -> T): T =
    runBlocking {
      body()
    }

  private fun IjentPath.toNioPath(): IjentNioPath =
    IjentNioPath(
      ijentPath = this,
      nioFs = this@IjentNioFileSystem,
    )
}