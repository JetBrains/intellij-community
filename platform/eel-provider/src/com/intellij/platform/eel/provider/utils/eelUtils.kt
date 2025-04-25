// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.OwnedBuilder
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.*

@ApiStatus.Internal
fun EelExecApi.fetchLoginShellEnvVariablesBlocking(): Map<String, String> {
  return runBlockingMaybeCancellable { fetchLoginShellEnvVariables() }
}

@ApiStatus.Internal
fun EelPlatform.toOs(): OS {
  return when (this) {
    is EelPlatform.Windows -> OS.Windows
    is EelPlatform.Linux -> OS.Linux
    is EelPlatform.Darwin -> OS.macOS
    is EelPlatform.FreeBSD -> OS.FreeBSD
  }
}

@ApiStatus.Internal
fun EelPlatform.toPathManagerOs(): PathManager.OS =
  when (this) {
    is EelPlatform.Windows -> PathManager.OS.WINDOWS
    is EelPlatform.Darwin -> PathManager.OS.MACOS
    is EelPlatform.Linux -> PathManager.OS.LINUX
    is EelPlatform.FreeBSD -> PathManager.OS.GENERIC_UNIX
  }

private val archMap by lazy {
  BidirectionalMap<CpuArch, EelPlatform.Arch>().apply {
    put(CpuArch.X86, EelPlatform.Arch.X86)
    put(CpuArch.X86_64, EelPlatform.Arch.X86_64)
    put(CpuArch.ARM32, EelPlatform.Arch.ARM_32)
    put(CpuArch.ARM64, EelPlatform.Arch.ARM_64)
    put(CpuArch.OTHER, EelPlatform.Arch.Unknown)
    put(CpuArch.UNKNOWN, EelPlatform.Arch.Unknown)
  }
}

@ApiStatus.Internal
fun CpuArch.toEelArch(): EelPlatform.Arch = archMap[this] ?: EelPlatform.Arch.Unknown

@ApiStatus.Internal
fun EelPlatform.Arch.toCpuArch(): CpuArch = archMap.getKeysByValue(this)?.single() ?: CpuArch.UNKNOWN

@Throws(FileSystemException::class)
@ApiStatus.Internal
fun <T, E : EelFsError> EelResult<T, E>.getOrThrowFileSystemException(): T =
  when (this) {
    is EelResult.Ok -> value
    is EelResult.Error -> error.throwFileSystemException()
  }

@Throws(FileSystemException::class)
@ApiStatus.Internal
suspend fun <T, E : EelFsError, O : OwnedBuilder<EelResult<T, E>>> O.getOrThrowFileSystemException(): T =
  when (val v = eelIt()) {
    is EelResult.Ok -> v.value
    is EelResult.Error -> v.error.throwFileSystemException()
  }

// TODO There's java.nio.file.FileSystemLoopException, so ELOOP should be added to all error codes for a decent support of all exceptions.
@Throws(FileSystemException::class)
@ApiStatus.Internal
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