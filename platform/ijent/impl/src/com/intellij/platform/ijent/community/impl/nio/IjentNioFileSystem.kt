// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.IjentInfo
import com.intellij.platform.ijent.IjentPosixInfo
import com.intellij.platform.ijent.IjentWindowsInfo
import com.intellij.platform.ijent.fs.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService

class IjentNioFileSystem internal constructor(
  private val fsProvider: IjentNioFileSystemProvider,
  internal val ijent: FsAndUserApi,
) : FileSystem() {
  /**
   * Just a bunch of parts of [com.intellij.platform.ijent.IjentApi] with proper types. It was introduced to avoid type upcasting.
   */
  internal sealed interface FsAndUserApi {
    val fs: IjentFileSystemApi
    val userInfo: IjentInfo.User

    data class Posix(override val fs: IjentFileSystemPosixApi, override val userInfo: IjentPosixInfo.User) : FsAndUserApi
    data class Windows(override val fs: IjentFileSystemWindowsApi, override val userInfo: IjentWindowsInfo.User) : FsAndUserApi
  }

  override fun close() {
    // Seems that there should be nothing.
  }

  override fun provider(): IjentNioFileSystemProvider = fsProvider

  override fun isOpen(): Boolean =
    ijent.fs.coroutineScope.isActive

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String =
    when (ijent) {
      is FsAndUserApi.Posix -> "/"
      is FsAndUserApi.Windows -> "\\"
    }

  override fun getRootDirectories(): Iterable<IjentNioPath> = fsBlocking {
    when (val fs = ijent.fs) {
      is IjentFileSystemPosixApi -> listOf(getPath("/"))
      is IjentFileSystemWindowsApi -> fs.getRootDirectories().map { it.toNioPath() }
    }
  }

  override fun getFileStores(): Iterable<FileStore> =
    listOf(IjentNioFileStore(ijent.fs))

  override fun supportedFileAttributeViews(): Set<String> =
    when (ijent) {
      is FsAndUserApi.Windows ->
        setOf("basic", "dos", "acl", "owner", "user")
      is FsAndUserApi.Posix ->
        setOf(
          "basic", "posix", "unix", "owner",
          "user",  // TODO Works only on BSD/macOS.
        )
    }

  override fun getPath(first: String, vararg more: String): IjentNioPath {
    val os = when (ijent) {
      is FsAndUserApi.Posix -> IjentPath.Absolute.OS.UNIX
      is FsAndUserApi.Windows -> IjentPath.Absolute.OS.WINDOWS
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