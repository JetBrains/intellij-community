// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.ijent.IjentId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

// TODO Integrate case-(in)sensitiveness into the interface.

@ApiStatus.Experimental
sealed interface IjentFileSystemApi {
  /**
   * The same [IjentId] as in the corresponding [com.intellij.platform.ijent.IjentApi].
   */
  val id: IjentId

  /**
   * The same [CoroutineScope] as in the corresponding [com.intellij.platform.ijent.IjentApi].
   */
  @Deprecated("API should avoid exposing coroutine scopes")
  val coroutineScope: CoroutineScope

  /**
   * A user may have no home directory on Unix-like systems, for example, the user `nobody`.
   */
  suspend fun userHome(): IjentPath.Absolute?

  /**
   * Returns names of files in a directory. If [path] is a symlink, it will be resolved, but no symlinks are resolved among children.
   */
  suspend fun listDirectory(path: IjentPath.Absolute): ListDirectory

  @Suppress("unused")
  sealed interface ListDirectory : IjentFsResult {
    interface Ok : ListDirectory, IjentFsResult.Ok<Collection<String>>
    sealed interface DoesNotExist : ListDirectory, IjentFsResult.Error
    sealed interface PermissionDenied : ListDirectory, IjentFsResult.Error
    sealed interface NotDirectory : ListDirectory, IjentFsResult.Error
    sealed interface NotFile : ListDirectory, IjentFsResult.Error
  }

  /**
   * Returns names of files in a directory. If [path] is a symlink, it will be resolved regardless of [resolveSymlinks].
   *  TODO Is it an expected behaviour?
   *
   * [resolveSymlinks] controls resolution of symlinks among children.
   *  TODO The behaviour is different from resolveSymlinks in [stat]. To be fixed.
   */
  suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean = true,
  ): ListDirectoryWithAttrs<out IjentFileInfo>

  @Suppress("unused")
  sealed interface ListDirectoryWithAttrs<FI : IjentFileInfo> : IjentFsResult {
    interface Ok<FI : IjentFileInfo> : ListDirectoryWithAttrs<FI>, IjentFsResult.Ok<Collection<FI>>
    sealed interface DoesNotExist<FI : IjentFileInfo> : ListDirectoryWithAttrs<FI>, IjentFsResult.Error
    sealed interface PermissionDenied<FI : IjentFileInfo> : ListDirectoryWithAttrs<FI>, IjentFsResult.Error
    sealed interface NotDirectory<FI : IjentFileInfo> : ListDirectoryWithAttrs<FI>, IjentFsResult.Error
    sealed interface NotFile<FI : IjentFileInfo> : ListDirectoryWithAttrs<FI>, IjentFsResult.Error
  }

  /**
   * Resolves all symlinks in the path. Corresponds to realpath(3) on Unix and GetFinalPathNameByHandle on Windows.
   */
  suspend fun canonicalize(path: IjentPath.Absolute): Canonicalize

  sealed interface Canonicalize : IjentFsResult {
    interface Ok : Canonicalize, IjentFsResult.Ok<IjentPath.Absolute>
    sealed interface DoesNotExist : Canonicalize, IjentFsResult.Error
    sealed interface PermissionDenied : Canonicalize, IjentFsResult.Error
    sealed interface NotDirectory : Canonicalize, IjentFsResult.Error
    sealed interface NotFile : Canonicalize, IjentFsResult.Error
  }

  /**
   * Similar to stat(2) and lstat(2). [resolveSymlinks] has an impact only on [IjentFileInfo.fileType] if [path] points on a symlink.
   */
  suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): Stat<out IjentFileInfo>

  sealed interface Stat<FI : IjentFileInfo> : IjentFsResult {
    interface Ok<FI : IjentFileInfo> : Stat<FI>, IjentFsResult.Ok<FI>
    sealed interface DoesNotExist<FI : IjentFileInfo> : Stat<FI>, IjentFsResult.Error
    sealed interface PermissionDenied<FI : IjentFileInfo> : Stat<FI>, IjentFsResult.Error
    sealed interface NotDirectory<FI : IjentFileInfo> : Stat<FI>, IjentFsResult.Error
    sealed interface NotFile<FI : IjentFileInfo> : Stat<FI>, IjentFsResult.Error
  }

  /**
   * on Unix return true if both paths have the same inode.
   * On Windows some heuristics are used, for more details see https://docs.rs/same-file/1.0.6/same_file/
   */
  suspend fun sameFile(source: IjentPath.Absolute, target: IjentPath.Absolute): SameFile

  sealed interface SameFile : IjentFsResult {
    interface Ok : SameFile, IjentFsResult.Ok<Boolean>
    sealed interface DoesNotExist : SameFile, IjentFsResult.Error
    sealed interface PermissionDenied : SameFile, IjentFsResult.Error
    sealed interface NotDirectory : SameFile, IjentFsResult.Error
    sealed interface NotFile : SameFile, IjentFsResult.Error
  }

  suspend fun fileReader(path: IjentPath.Absolute): FileReader

  sealed interface FileReader : IjentFsResult {
    interface Ok : FileReader, IjentFsResult.Ok<IjentOpenedFile.Reader>
    sealed interface DoesNotExist : FileReader, IjentFsResult.Error
    sealed interface PermissionDenied : FileReader, IjentFsResult.Error
    sealed interface NotDirectory : FileReader, IjentFsResult.Error
    sealed interface NotFile : FileReader, IjentFsResult.Error
  }

  /**
   * If [append] is false, the file is erased.
   */
  suspend fun fileWriter(
    path: IjentPath.Absolute,
    append: Boolean = false,
    creationMode: FileWriterCreationMode = FileWriterCreationMode.ALLOW_CREATE,
  ): FileWriter

  sealed interface FileWriter : IjentFsResult {
    interface Ok : FileWriter, IjentFsResult.Ok<IjentOpenedFile.Writer>
    sealed interface DoesNotExist : FileWriter, IjentFsResult.Error
    sealed interface PermissionDenied : FileWriter, IjentFsResult.Error
    sealed interface NotDirectory : FileWriter, IjentFsResult.Error
    sealed interface NotFile : FileWriter, IjentFsResult.Error
  }

  enum class FileWriterCreationMode {
    ALLOW_CREATE, ONLY_CREATE, ONLY_OPEN_EXISTING,
  }
}

sealed interface IjentOpenedFile {
  val path: IjentPath.Absolute

  @Throws(CloseException::class)
  suspend fun close()

  class CloseException(override val error: CloseError) : IjentFsResult.IjentFsIOException() {
    sealed interface CloseError : IjentFsResult.ErrorBase {
      sealed interface DoesNotExist : CloseError, IjentFsResult.Error
      sealed interface PermissionDenied : CloseError, IjentFsResult.Error
      sealed interface NotDirectory : CloseError, IjentFsResult.Error
      sealed interface NotFile : CloseError, IjentFsResult.Error
    }
  }

  fun tell(): Long

  suspend fun seek(offset: Long, whence: SeekWhence): Seek

  sealed interface Seek : IjentFsResult {
    interface Ok : IjentFsResult.Ok<Long>, Seek
    sealed interface DoesNotExist : Seek, IjentFsResult.Error
    sealed interface PermissionDenied : Seek, IjentFsResult.Error
    sealed interface NotDirectory : Seek, IjentFsResult.Error
    sealed interface NotFile : Seek, IjentFsResult.Error

    interface InvalidValue : Seek, IjentFsResult.Error
  }

  enum class SeekWhence {
    START, CURRENT, END,
  }

  interface Reader : IjentOpenedFile {
    suspend fun read(buf: ByteBuffer): Read

    sealed interface Read : IjentFsResult {
      interface Ok : Read, IjentFsResult.Ok<Int>
      sealed interface DoesNotExist : Read, IjentFsResult.Error
      sealed interface PermissionDenied : Read, IjentFsResult.Error
      sealed interface NotDirectory : Read, IjentFsResult.Error
      sealed interface NotFile : Read, IjentFsResult.Error
    }
  }

  interface Writer : IjentOpenedFile {
    suspend fun write(buf: ByteBuffer): Write

    sealed interface Write : IjentFsResult {
      interface Ok : Write, IjentFsResult.Ok<Int>
      sealed interface DoesNotExist : Write, IjentFsResult.Error
      sealed interface PermissionDenied : Write, IjentFsResult.Error
      sealed interface NotDirectory : Write, IjentFsResult.Error
      sealed interface NotFile : Write, IjentFsResult.Error
    }

    // There's no flush(). It's supposed that `write` flushes.

    @Throws(TruncateException::class)
    suspend fun truncate()

    class TruncateException(override val error: TruncateError) : IjentFsResult.IjentFsIOException() {
      sealed interface TruncateError : IjentFsResult.ErrorBase {
        sealed interface DoesNotExist : TruncateError, IjentFsResult.Error
        sealed interface PermissionDenied : TruncateError, IjentFsResult.Error
        sealed interface NotDirectory : TruncateError, IjentFsResult.Error
        sealed interface NotFile : TruncateError, IjentFsResult.Error
      }
    }
  }
}

interface IjentFileSystemPosixApi : IjentFileSystemApi {
  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFileSystemApi.ListDirectoryWithAttrs<IjentPosixFileInfo>

  override suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): IjentFileSystemApi.Stat<IjentPosixFileInfo>
}

interface IjentFileSystemWindowsApi : IjentFileSystemApi {
  suspend fun getRootDirectories(): Collection<IjentPath.Absolute>

  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFileSystemApi.ListDirectoryWithAttrs<IjentWindowsFileInfo>

  override suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): IjentFileSystemApi.Stat<IjentWindowsFileInfo>
}