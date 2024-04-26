// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentPath
import com.intellij.platform.ijent.fs.getOrThrow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService

class IjentNioFileSystem(
  private val fsProvider: IjentNioFileSystemProvider,
  internal val ijentFsApi: IjentFileSystemApi,
) : FileSystem() {
  internal val isWindows = ijentFsApi.isWindows

  override fun close() {
    // Seems that there should be nothing.
  }

  override fun provider(): IjentNioFileSystemProvider = fsProvider

  override fun isOpen(): Boolean =
    ijentFsApi.coroutineScope.isActive

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String =
    if (ijentFsApi.isWindows) "\\" else "/"

  override fun getRootDirectories(): Iterable<IjentNioPath> = fsBlocking {
    ijentFsApi.getRootDirectories().map { it.toNioPath() }
  }

  override fun getFileStores(): Iterable<FileStore> =
    listOf(IjentNioFileStore(ijentFsApi))

  override fun supportedFileAttributeViews(): Set<String> =
    if (isWindows)
      setOf("basic", "dos", "acl", "owner", "user")
    else setOf(
      "basic", "posix", "unix", "owner",
      "user",  // TODO Works only on BSD/macOS.
    )

  override fun getPath(first: String, vararg more: String): IjentNioPath {
    val os = if (isWindows) IjentPath.Absolute.OS.WINDOWS else IjentPath.Absolute.OS.UNIX
    return IjentPath.parse(first, os)
      .getOrThrow()
      .resolve(IjentPath.Relative.build(*more).getOrThrow())
      .getOrThrow()
      .toNioPath()
  }

  override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
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