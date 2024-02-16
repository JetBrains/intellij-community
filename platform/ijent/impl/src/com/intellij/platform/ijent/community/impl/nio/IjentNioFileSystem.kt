// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.*
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
  override fun close() {
    // Seems that there should be nothing.
  }

  override fun provider(): IjentNioFileSystemProvider = fsProvider

  override fun isOpen(): Boolean =
    ijentFsApi.coroutineScope.isActive

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String =
    when (ijentFsApi) {
      is IjentFileSystemPosixApi -> "/"
      is IjentFileSystemWindowsApi -> "\\"
    }

  override fun getRootDirectories(): Iterable<IjentNioPath> = fsBlocking {
    when (ijentFsApi) {
      is IjentFileSystemPosixApi -> listOf(getPath("/"))
      is IjentFileSystemWindowsApi -> ijentFsApi.getRootDirectories().map { it.toNioPath() }
    }
  }

  override fun getFileStores(): Iterable<FileStore> =
    listOf(IjentNioFileStore(ijentFsApi))

  override fun supportedFileAttributeViews(): Set<String> =
    when (ijentFsApi) {
      is IjentFileSystemWindowsApi ->
        setOf("basic", "dos", "acl", "owner", "user")
      is IjentFileSystemPosixApi ->
        setOf(
          "basic", "posix", "unix", "owner",
          "user",  // TODO Works only on BSD/macOS.
        )
    }

  override fun getPath(first: String, vararg more: String): IjentNioPath {
    val os = when (ijentFsApi) {
      is IjentFileSystemPosixApi -> IjentPath.Absolute.OS.UNIX
      is IjentFileSystemWindowsApi -> IjentPath.Absolute.OS.WINDOWS
    }
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