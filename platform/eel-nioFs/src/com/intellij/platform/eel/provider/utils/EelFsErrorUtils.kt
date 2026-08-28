// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.OwnedBuilder
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.fs.EelOpenedFile
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.ReadOnlyFileSystemException
import kotlin.coroutines.cancellation.CancellationException

@Throws(FileSystemException::class)
@ApiStatus.Internal
fun <T, E : EelFsError> EelResult<T, E>.getOrThrowFileSystemException(): T =
  when (this) {
    is EelResult.Ok -> value
    is EelResult.Error -> error.throwFileSystemException()
  }

@Throws(FileSystemException::class)
@ApiStatus.Internal
suspend fun <T, E : EelFsError, O : OwnedBuilder<EelResult<T, E>>> O.getOrThrowFileSystemException(): T {
  try {
    val v = eelIt()
    return when (v) {
      is EelResult.Ok -> v.value
      is EelResult.Error -> v.error.throwFileSystemException()
    }
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (ioe: IOException) {
    throw ioe
  }
  catch (t: Throwable) {
    @Suppress("RethrowControlFlowExceptionWithUtil")
    if (t is ControlFlowException) throw t
    throw IOException(t.message.orEmpty(), t)
  }
}

// TODO There's java.nio.file.FileSystemLoopException, so ELOOP should be added to all error codes for a decent support of all exceptions.
@Throws(FileSystemException::class)
@ApiStatus.Internal
fun EelFsError.throwFileSystemException(): Nothing {
  throw when (this) {
    is EelFsError.DoesNotExist -> NoSuchFileException(where.toString(), null, message.takeIf { it.isNotEmpty() })
    is EelFsError.NotFile -> FileSystemException(where.toString(), null, "Is a directory")
    is EelFsError.PermissionDenied -> AccessDeniedException(where.toString(), null, message.takeIf { it.isNotEmpty() })
    is EelFsError.NotDirectory -> NotDirectoryException(where.toString())
    is EelFsError.AlreadyExists -> FileAlreadyExistsException(where.toString())
    is EelFsError.UnknownFile -> IOException("File is not opened")
    is EelFsError.DirNotEmpty -> DirectoryNotEmptyException(where.toString())
    is EelFsError.NameTooLong -> IllegalArgumentException("Name is too long")
    is EelFsError.NotEnoughSpace -> FileSystemException(where.toString(), null, "Not enough space")
    is EelFsError.ReadOnlyFileSystem -> ReadOnlyFileSystemException()
    is EelOpenedFile.SeekError.InvalidValue -> IllegalArgumentException(message)
    is EelOpenedFile.Reader.ReadError.InvalidValue -> IllegalArgumentException(message)
    is EelOpenedFile.Writer.TruncateError.NegativeOffset,
    is EelOpenedFile.Writer.TruncateError.OffsetTooBig,
      -> throw IllegalArgumentException(message)
    is EelOpenedFile.Writer.WriteError.InvalidValue -> throw IllegalArgumentException(message)
    is EelFileSystemApi.DeleteError.UnresolvedLink -> throw FileSystemException(where.toString(), null, message)
    is EelFileSystemApi.FileReaderError.FileBiggerThanRequested ->
      throw FileSystemException(where.toString(), null, "File is too big").apply { initCause(FileTooBigException("File is too big")) }
    is EelFsError.Other -> FileSystemException(where.toString(), null, message.takeIf { it.isNotEmpty() })
  }
}
