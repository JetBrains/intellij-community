// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")
package com.intellij.platform.ijent.fs.nio

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemApi.*
import com.intellij.platform.ijent.fs.IjentFsResult
import com.intellij.platform.ijent.fs.IjentFsResultError
import com.intellij.platform.ijent.fs.IjentFsResultError.*
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.spi.FileSystemProvider

/**
 * Returns an adapter from [IjentFileSystemApi] to [java.nio.file.FileSystem]. The adapter is automatically registered in advance,
 * also it is automatically closed when it is needed.
 *
 * The function is idempotent and thread-safe.
 */
fun IjentFileSystemApi.asNioFileSystem(): FileSystem {
  val nioFsProvider = FileSystemProvider.installedProviders()
    .filterIsInstance<IjentNioFileSystemProvider>()
    .single()
  val uri = URI(nioFsProvider.scheme, id.id, null, null)
  return try {
    nioFsProvider.getFileSystem(uri)
  }
  catch (ignored: FileSystemNotFoundException) {
    try {
      nioFsProvider.newFileSystem(uri, mutableMapOf<String, Any>())
    }
    catch (ignored: FileSystemAlreadyExistsException) {
      nioFsProvider.getFileSystem(uri)
    }
  }
}

@Throws(java.nio.file.FileSystemException::class)
internal fun <T, E : IjentFsResultError> IjentFsResult<T, E>.getOrThrow(file: String, otherFile: String? = null): T =
  when (this) {
    is IjentFsResult.Ok<T, E> -> result
    is IjentFsResult.Err<T, E> -> throw error.toFileSystemException(file, otherFile, message)
  }

// TODO Make a test with the reflection which would check all implementations of IjentFsResultError.
internal fun IjentFsResultError.toFileSystemException(file: String?, otherFile: String?, message: String): java.nio.file.FileSystemException =
  when (this) {
    is Generic -> when (this) {
      Generic.DoesNotExist -> java.nio.file.NoSuchFileException(file, otherFile, message)
      is Generic.PermissionDenied -> java.nio.file.AccessDeniedException(file, otherFile, message)
    }

    is ListDirectoryError -> when (this) {
      is ListDirectoryError.Generic -> generic.toFileSystemException(file, otherFile, message)
      ListDirectoryError.NotDirectory -> java.nio.file.NotDirectoryException(file)
    }

    is SameFileError -> error.toFileSystemException(path.toString(), null, message)

    else -> throw NotImplementedError(toString())
  }