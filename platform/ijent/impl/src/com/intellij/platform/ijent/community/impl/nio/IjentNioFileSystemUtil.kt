// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")

package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.platform.eel.path.EelPath
import com.intellij.util.text.nullize
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.*

@Throws(FileSystemException::class)
internal fun <T, E : EelFsError> EelResult<T, E>.getOrThrowFileSystemException(): T =
  when (this) {
    is EelResult.Ok -> value
    is EelResult.Error -> error.throwFileSystemException()
  }

// TODO There's java.nio.file.FileSystemLoopException, so ELOOP should be added to all error codes for a decent support of all exceptions.
@Throws(FileSystemException::class)
internal fun EelFsError.throwFileSystemException(): Nothing {
  throw when (this) {
    is EelFsError.DoesNotExist -> NoSuchFileException(where.toString(), null, message.nullize())
    is EelFsError.NotFile -> FileSystemException(where.toString(), null, "Is a directory")
    is EelFsError.PermissionDenied -> AccessDeniedException(where.toString(), null, message.nullize())
    is EelFsError.NotDirectory -> NotDirectoryException(where.toString())
    is EelFsError.AlreadyExists -> FileAlreadyExistsException(where.toString())
    is EelFsError.UnknownFile -> IOException("File is not opened")
    is EelFsError.DirNotEmpty -> DirectoryNotEmptyException(where.toString())
    is EelFsError.NameTooLong -> IllegalArgumentException("Name is too long")
    is EelFsError.NotEnoughSpace -> FileSystemException(where.toString(), null, "Not enough space")
    is EelFsError.ReadOnlyFileSystem -> ReadOnlyFileSystemException()
    is EelOpenedFile.SeekError.InvalidValue -> IllegalArgumentException(message)
    is EelOpenedFile.Reader.ReadError.InvalidValue -> IllegalArgumentException(message)
    is EelOpenedFile.Writer.TruncateException.NegativeOffset,
    is EelOpenedFile.Writer.TruncateException.OffsetTooBig,
      -> throw IllegalArgumentException(message)
    is EelOpenedFile.Writer.WriteError.InvalidValue -> throw IllegalArgumentException(message)
    is EelFileSystemApi.DeleteException.UnresolvedLink -> throw FileSystemException(where.toString(), null, message)
    is EelFsError.Other -> FileSystemException(where.toString(), null, message.nullize())
  }
}

internal fun Path.toEelPath(): EelPath =
  when {
    this is IjentNioPath -> eelPath

    isAbsolute -> throw InvalidPathException(toString(), "This path can't be converted to IjentPath")

    else -> EelPath.Relative.parse(toString())
  }

/**
 * We need to use a plain `runBlocking` here.
 * The IO call is supposed to be fast (several milliseconds in the worst case),
 * so the cost of spawning and destroying an additional thread in Dispatchers.Default would be too big.
 * Also, IJent does not require any outer lock in its implementation, so a deadlock is not possible.
 *
 * In addition, we suppress work stealing in this `runBlocking`, as it should return as fast as it can on its own.
 */
@Suppress("SSBasedInspection")
internal fun <T> fsBlocking(body: suspend () -> T): T = runBlocking(NestedBlockingEventLoop(Thread.currentThread())) {
  body()
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER", "ERROR_SUPPRESSION")
private class NestedBlockingEventLoop(override val thread: Thread) : kotlinx.coroutines.EventLoopImplBase() {
  override fun shouldBeProcessedFromContext(): Boolean = true
}