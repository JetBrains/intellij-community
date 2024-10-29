// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")

package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.platform.eel.path.EelPath
import com.intellij.util.text.nullize
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.nio.file.*
import kotlin.Throws
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine

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

internal fun <T> fsBlocking(body: suspend () -> T): T = invokeSuspending(body)

/**
 * Runs a suspending IO operation [block] in non-suspending code.
 * Normally, [kotlinx.coroutines.runBlocking] should be used in such cases,
 * but it has significant performance overhead: creation and installation of an [kotlinx.coroutines.EventLoop].
 *
 * Unfortunately, the execution of [block] may still launch coroutines, although they are very primitive.
 * To mitigate this, we use [Dispatchers.Unconfined] as an elementary event loop.
 * It does not change the final thread of execution,
 * as we are waiting for a monitor on the same thread where [invokeSuspending] was called.
 *
 * We manage to save up to 30% (300 microseconds) of performance cost in comparison with [kotlinx.coroutines.runBlocking],
 * which is important in case of many short IO operations.
 *
 * The invoked operation is non-cancellable, as one can expect from regular native-based IO calls.
 *
 * @see com.intellij.openapi.application.impl.runSuspend
 */
private fun <T> invokeSuspending(block: suspend () -> T): T {
  val run = RunSuspend<T>()
  block.startCoroutine(run)
  return run.await()
}

private class RunSuspend<T> : Continuation<T> {
  override val context: CoroutineContext = Dispatchers.Unconfined

  var result: Result<T>? = null

  override fun resumeWith(result: Result<T>) = synchronized(this) {
    this.result = result
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).notifyAll()
  }

  fun await(): T {
    synchronized(this) {
      while (true) {
        when (val result = this.result) {
          null -> @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).wait()
          else -> {
            return result.getOrThrow() // throw up failure
          }
        }
      }
    }
  }
}