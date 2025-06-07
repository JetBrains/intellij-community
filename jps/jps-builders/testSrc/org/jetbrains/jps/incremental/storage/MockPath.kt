// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import java.io.File
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

class MockPath(private val path: String) : Path {
  override fun getFileSystem(): FileSystem = MockFileSystem

  override fun isAbsolute(): Boolean = true

  override fun getRoot(): Path = this

  override fun getFileName(): Path = this

  override fun getParent(): Path? = null

  override fun getNameCount(): Int = 0

  override fun getName(index: Int): Path {
    if (index == 0) {
      return this
    }
    else {
      throw IllegalArgumentException()
    }
  }

  override fun subpath(beginIndex: Int, endIndex: Int): Path {
    throw UnsupportedOperationException()
  }

  override fun startsWith(other: Path): Boolean = startsWith(other.toString())

  override fun startsWith(other: String): Boolean = path.startsWith(other)

  override fun endsWith(other: Path): Boolean = endsWith(other.toString())

  override fun endsWith(other: String): Boolean = path.endsWith(other)

  override fun normalize(): Path = this

  override fun resolve(other: Path): Path = resolve(other.toString())

  override fun resolve(other: String): Path {
    throw UnsupportedOperationException()
  }

  override fun resolveSibling(other: Path): Path = resolveSibling(other.toString())

  override fun resolveSibling(other: String): Path {
    throw UnsupportedOperationException()
  }

  override fun relativize(other: Path): Path {
    throw UnsupportedOperationException()
  }

  override fun toUri(): URI {
    throw UnsupportedOperationException()
  }

  override fun toAbsolutePath(): Path = this

  override fun toRealPath(vararg options: LinkOption): Path = this

  override fun toFile(): File {
    throw UnsupportedOperationException()
  }

  override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey {
    throw UnsupportedOperationException()
  }

  override fun register(watcher: WatchService, events: Array<WatchEvent.Kind<*>>, vararg modifiers: WatchEvent.Modifier): WatchKey {
    throw UnsupportedOperationException()
  }

  override fun iterator(): MutableIterator<Path> = super.iterator()

  override fun compareTo(other: Path): Int = path.compareTo(other.toString())

  override fun toString(): String = path
}

private object MockFileSystem : FileSystem() {
  override fun provider(): FileSystemProvider? {
    throw UnsupportedOperationException()
  }

  override fun close() {
  }

  override fun isOpen(): Boolean = true

  override fun isReadOnly(): Boolean = true

  override fun getSeparator(): String = "/"

  override fun getRootDirectories(): Iterable<Path> = emptySet()

  override fun getFileStores(): Iterable<FileStore> = emptyList()

  override fun supportedFileAttributeViews(): Set<String> = emptySet()

  override fun getPath(first: String, vararg more: String): Path {
    throw UnsupportedOperationException()
  }

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher? {
    throw UnsupportedOperationException()
  }

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService? {
    throw UnsupportedOperationException()
  }

  override fun newWatchService(): WatchService? {
    throw UnsupportedOperationException()
  }

  override fun toString(): String = "mock"
}