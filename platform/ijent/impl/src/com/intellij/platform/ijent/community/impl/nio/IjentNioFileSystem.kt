// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import com.intellij.platform.ijent.fs.IjentFileSystemWindowsApi
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService

class IjentNioFileSystem internal constructor(
  private val fsProvider: IjentNioFileSystemProvider,
  internal val uri: URI,
) : FileSystem() {
  override fun close() {
    fsProvider.close(uri)
  }

  override fun provider(): IjentNioFileSystemProvider = fsProvider

  val ijentFs: IjentFileSystemApi
    @ApiStatus.Internal
    get() =
      fsProvider.ijentFsApi(uri)
      ?: throw java.nio.file.FileSystemException("`$uri` was removed from IJent FS providers")

  override fun isOpen(): Boolean =
    fsProvider.ijentFsApi(uri) != null

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String =
    when (ijentFs) {
      is IjentFileSystemPosixApi -> "/"
      is IjentFileSystemWindowsApi -> "\\"
    }

  override fun getRootDirectories(): Iterable<IjentNioPath> = fsBlocking {
    when (val fs = ijentFs) {
      is IjentFileSystemPosixApi -> listOf(getPath("/"))
      is IjentFileSystemWindowsApi -> fs.getRootDirectories().map { it.toNioPath() }
    }
  }

  override fun getFileStores(): Iterable<FileStore> {
    val home = fsBlocking {
      ijentFs.userHome()
    }
    return listOf(IjentNioFileStore(home!!, ijentFs))
  }

  override fun supportedFileAttributeViews(): Set<String> =
    when (ijentFs) {
      is IjentFileSystemWindowsApi ->
        setOf("basic", "dos", "acl", "owner", "user")
      is IjentFileSystemPosixApi ->
        setOf(
          "basic", "posix", "unix", "owner",
          "user",  // TODO Works only on BSD/macOS.
        )
    }

  override fun getPath(first: String, vararg more: String): IjentNioPath {
    val os = when (ijentFs) {
      is IjentFileSystemPosixApi -> EelPath.Absolute.OS.UNIX
      is IjentFileSystemWindowsApi -> EelPath.Absolute.OS.WINDOWS
    }
    return EelPath.parseE(first, os)
      .resolveE(EelPath.Relative.buildE(*more))
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

  private fun EelPath.toNioPath(): IjentNioPath =
    IjentNioPath(
      eelPath = this,
      nioFs = this@IjentNioFileSystem,
      cachedAttributes = null,
    )
}