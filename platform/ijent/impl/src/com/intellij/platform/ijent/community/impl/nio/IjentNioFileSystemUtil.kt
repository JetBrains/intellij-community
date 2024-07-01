// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")

package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.*
import com.intellij.util.text.nullize
import java.io.IOException
import java.nio.channels.NonWritableChannelException
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
    is IjentFsError.DoesNotExist -> NoSuchFileException(where.toString(), null, message.nullize())
    is IjentFsError.NotFile -> FileSystemException(where.toString(), null, "Is a directory")
    is IjentFsError.PermissionDenied -> AccessDeniedException(where.toString(), null, message.nullize())
    is IjentFsError.NotDirectory -> NotDirectoryException(where.toString())
    is IjentFsError.AlreadyDeleted -> NoSuchFileException(where.toString())
    is IjentFsError.AlreadyExists -> FileAlreadyExistsException(where.toString())
    is IjentFsError.UnknownFile -> IOException("File is not opened")
    is IjentOpenedFile.SeekError.InvalidValue -> throw IllegalArgumentException(message)
    is IjentFsError.Other -> FileSystemException(where.toString(), null, message.nullize())
    is IjentOpenedFile.Reader.ReadError.InvalidValue -> TODO()
    is IjentFileSystemApi.DeleteException.DirNotEmpty -> DirectoryNotEmptyException(where.toString())
    is IjentOpenedFile.Writer.TruncateException.NegativeOffset,
    is IjentOpenedFile.Writer.TruncateException.OffsetTooBig -> throw IllegalArgumentException(message)
    is IjentOpenedFile.Writer.TruncateException.ReadOnlyFs -> throw NonWritableChannelException()
  }
}

internal fun Path.toIjentPath(isWindows: Boolean): IjentPath =
  when {
    this is IjentNioPath -> ijentPath

    isAbsolute -> throw InvalidPathException(toString(), "This path can't be converted to IjentPath")

    else -> IjentPath.Relative.parse(toString()).getOrThrow()
  }