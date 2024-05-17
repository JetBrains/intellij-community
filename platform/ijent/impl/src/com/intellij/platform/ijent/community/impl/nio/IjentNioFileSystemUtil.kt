// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")

package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.*
import com.intellij.util.text.nullize
import java.nio.file.*

/**
 * Returns an adapter from [IjentFileSystemApi] to [java.nio.file.FileSystem]. The adapter is automatically registered in advance,
 * also it is automatically closed when it is needed.
 *
 * The function is idempotent and thread-safe.
 */
fun IjentFileSystemApi.asNioFileSystem(): FileSystem {
  val nioFsProvider = IjentNioFileSystemProvider.getInstance()
  val uri = id.uri
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

@Throws(FileSystemException::class)
internal fun <T, E : IjentFsError> IjentFsResult<T, E>.getOrThrowFileSystemException(): T =
  when (this) {
    is IjentFsResult.Ok -> value
    is IjentFsResult.Error -> error.throwFileSystemException()
  }

@Throws(FileSystemException::class)
internal fun IjentFsError.throwFileSystemException(): Nothing {
  throw when (this) {
    is IjentFsError.DoesNotExist, is IjentFsError.NotFile -> NoSuchFileException(where.toString(), null, message.nullize())
    is IjentFsError.PermissionDenied -> AccessDeniedException(where.toString(), null, message.nullize())
    is IjentFsError.NotDirectory -> NotDirectoryException(where.toString())
    is IjentOpenedFile.SeekError.InvalidValue -> TODO()
  }
}

internal fun Path.toIjentPath(isWindows: Boolean): IjentPath =
  when {
    this is IjentNioPath -> ijentPath

    isAbsolute -> throw InvalidPathException(toString(), "This path can't be converted to IjentPath")

    else -> IjentPath.Relative.parse(toString()).getOrThrow()
  }