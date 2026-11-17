// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.directorySeparators
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.EelDescriptorOwner
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import com.intellij.platform.ijent.fs.IjentFileSystemWindowsApi
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService

class IjentNioFileSystem internal constructor(
  private val fsProvider: IjentNioFileSystemProvider,
  internal val uri: URI,
) : FileSystem(), EelDescriptorOwner {

  override val eelDescriptor: EelDescriptor = ijentFs.descriptor

  override fun close() {
    fsProvider.close(uri)
  }

  override fun provider(): IjentNioFileSystemProvider = fsProvider

  val ijentFs: IjentFileSystemApi
    @ApiStatus.Internal
    @Throws(FileSystemNotFoundException::class)
    get() =
      fsProvider.ijentFsApi(uri)
      ?: throw FileSystemNotFoundException("`$uri` was removed from IJent FS providers")

  override fun isOpen(): Boolean =
    fsProvider.ijentFsApi(uri) != null

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String =
    when (eelDescriptor.osFamily) {
      EelOsFamily.Posix -> "/"
      EelOsFamily.Windows -> "\\"
    }

  override fun getRootDirectories(): Iterable<IjentNioPath> =
    when (val fs = ijentFs) {
      is IjentFileSystemPosixApi -> listOf(getPath("/"))
      is IjentFileSystemWindowsApi -> fs.fsBlocking { getRootDirectories() }.map { it.toNioPath() }
    }

  override fun getFileStores(): Iterable<FileStore> {
    val home = ijentFs.user.home
    return listOf(IjentNioFileStore(home, ijentFs))
  }

  override fun supportedFileAttributeViews(): Set<String> =
    when (eelDescriptor.osFamily) {
      EelOsFamily.Windows ->
        setOf("basic", "dos", "acl", "owner", "user")
      EelOsFamily.Posix ->
        setOf(
          "basic", "posix", "unix", "owner",
          "user",  // TODO Works only on BSD/macOS.
        )
    }

  override fun getPath(first: String, vararg more: String): IjentNioPath {
    return try {
      more.fold(EelPath.parse(first, eelDescriptor)) { path, newPart -> path.resolve(newPart) }.toNioPath()
    }
    catch (_: EelPathException) {
      RelativeIjentNioPath(first.split(*eelDescriptor.osFamily.directorySeparators) + more, this)
    }
  }

  override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
    TODO("Not yet implemented")
  }

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
    TODO("Not yet implemented")
  }

  override fun newWatchService(): WatchService =
    IjentNioWatchService(ijentFs, this)

  override fun equals(other: Any?): Boolean =
    other is IjentNioFileSystem && other.uri == uri && other.fsProvider == fsProvider

  override fun hashCode(): Int =
    fsProvider.hashCode() * 31 + uri.hashCode()

  override fun toString(): String =
    "IjentNioFileSystem(uri=$uri)"

  private fun EelPath.toNioPath(): IjentNioPath =
    AbsoluteIjentNioPath(
      eelPath = this,
      nioFs = this@IjentNioFileSystem,
      cachedAttributes = null,
    )
}