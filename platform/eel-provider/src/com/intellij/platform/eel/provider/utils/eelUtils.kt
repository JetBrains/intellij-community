// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.OwnedBuilder
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.util.system.CpuArch
import com.intellij.util.text.nullize
import java.io.IOException
import java.nio.file.*

fun EelExecApi.fetchLoginShellEnvVariablesBlocking(): Map<String, String> {
  return runBlockingMaybeCancellable { fetchLoginShellEnvVariables() }
}

fun CpuArch.toEelArch(): EelPlatform.Arch = when (this) {
  CpuArch.X86 -> EelPlatform.Arch.X86
  CpuArch.X86_64 -> EelPlatform.Arch.X86_64
  CpuArch.ARM32 -> EelPlatform.Arch.ARM_32
  CpuArch.ARM64 -> EelPlatform.Arch.ARM_64
  CpuArch.OTHER, CpuArch.UNKNOWN -> EelPlatform.Arch.Unknown
}

@Throws(FileSystemException::class)
fun <T, E : EelFsError> EelResult<T, E>.getOrThrowFileSystemException(): T =
  when (this) {
    is EelResult.Ok -> value
    is EelResult.Error -> error.throwFileSystemException()
  }

@Throws(FileSystemException::class)
suspend fun <T, E : EelFsError, O : OwnedBuilder<EelResult<T, E>>> O.getOrThrowFileSystemException(): T =
  when (val v = eelIt()) {
    is EelResult.Ok -> v.value
    is EelResult.Error -> v.error.throwFileSystemException()
  }

// TODO There's java.nio.file.FileSystemLoopException, so ELOOP should be added to all error codes for a decent support of all exceptions.
@Throws(FileSystemException::class)
fun EelFsError.throwFileSystemException(): Nothing {
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
    is EelOpenedFile.Writer.TruncateError.NegativeOffset,
    is EelOpenedFile.Writer.TruncateError.OffsetTooBig,
      -> throw IllegalArgumentException(message)
    is EelOpenedFile.Writer.WriteError.InvalidValue -> throw IllegalArgumentException(message)
    is EelFileSystemApi.DeleteError.UnresolvedLink -> throw FileSystemException(where.toString(), null, message)
    is EelFsError.Other -> FileSystemException(where.toString(), null, message.nullize())
  }
}