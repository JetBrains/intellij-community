// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

class TransitionalIjentWslFileSystem(
  private val provider: TransitionalIjentWslFileSystemProvider,
  private val localFs: FileSystem,
  private val ijentFs: FileSystem,
) : FileSystem() {
  override fun close() {
    localFs.use {
      ijentFs.close()
    }
  }

  override fun provider(): FileSystemProvider =
    provider

  override fun isOpen(): Boolean {
    check(localFs.isOpen == ijentFs.isOpen)
    return localFs.isOpen
  }

  override fun isReadOnly(): Boolean {
    check(localFs.isReadOnly == ijentFs.isReadOnly)
    return localFs.isReadOnly
  }

  override fun getSeparator(): String =
    localFs.separator

  override fun getRootDirectories(): Iterable<Path> =
    localFs.rootDirectories

  override fun getFileStores(): Iterable<FileStore> =
    localFs.fileStores

  override fun supportedFileAttributeViews(): Set<String> =
    localFs.supportedFileAttributeViews()

  override fun getPath(first: String, vararg more: String?): Path =
    localFs.getPath(first, *more)

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher =
    localFs.getPathMatcher(syntaxAndPattern)

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
    localFs.userPrincipalLookupService

  override fun newWatchService(): WatchService =
    localFs.newWatchService()
}