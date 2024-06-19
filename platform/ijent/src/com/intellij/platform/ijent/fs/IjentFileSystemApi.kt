// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ijent.IjentId
import kotlinx.coroutines.CoroutineScope
import java.nio.ByteBuffer

// TODO Integrate case-(in)sensitiveness into the interface.

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
  suspend fun listDirectory(path: IjentPath.Absolute): IjentFsResult<
    Collection<String>,
    ListDirectoryError>

  /**
   * Returns names of files in a directory and the attributes of the corresponding files.
   * If [path] is a symlink, it will be resolved regardless of [resolveSymlinks].
   *  TODO Is it an expected behaviour?
   *
   * [resolveSymlinks] controls resolution of symlinks among children.
   *  TODO The behaviour is different from resolveSymlinks in [stat]. To be fixed.
   */
  suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean = true,
  ): IjentFsResult<
    out Collection<Pair<String, IjentFileInfo>>,
    ListDirectoryError>

  @Suppress("unused")
  sealed interface ListDirectoryError : IjentFsError {
    interface DoesNotExist : ListDirectoryError, IjentFsError.DoesNotExist
    interface PermissionDenied : ListDirectoryError, IjentFsError.PermissionDenied
    interface NotDirectory : ListDirectoryError, IjentFsError.NotDirectory
    interface Other : ListDirectoryError, IjentFsError.Other
  }

  /**
   * Resolves all symlinks in the path. Corresponds to realpath(3) on Unix and GetFinalPathNameByHandle on Windows.
   */
  suspend fun canonicalize(path: IjentPath.Absolute): IjentFsResult<
    IjentPath.Absolute,
    CanonicalizeError>

  sealed interface CanonicalizeError : IjentFsError {
    interface DoesNotExist : CanonicalizeError, IjentFsError.DoesNotExist
    interface PermissionDenied : CanonicalizeError, IjentFsError.PermissionDenied
    interface NotDirectory : CanonicalizeError, IjentFsError.NotDirectory
    interface NotFile : CanonicalizeError, IjentFsError.NotFile
    interface Other : CanonicalizeError, IjentFsError.Other
  }

  /**
   * Similar to stat(2) and lstat(2). [resolveSymlinks] has an impact only on [IjentFileInfo.fileType] if [path] points on a symlink.
   */
  suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): IjentFsResult<out IjentFileInfo, StatError>

  sealed interface StatError : IjentFsError {
    interface DoesNotExist : StatError, IjentFsError.DoesNotExist
    interface PermissionDenied : StatError, IjentFsError.PermissionDenied
    interface NotDirectory : StatError, IjentFsError.NotDirectory
    interface NotFile : StatError, IjentFsError.NotFile
    interface Other : StatError, IjentFsError.Other
  }

  /**
   * on Unix return true if both paths have the same inode.
   * On Windows some heuristics are used, for more details see https://docs.rs/same-file/1.0.6/same_file/
   */
  suspend fun sameFile(source: IjentPath.Absolute, target: IjentPath.Absolute): IjentFsResult<
    Boolean,
    SameFileError>

  sealed interface SameFileError : IjentFsError {
    interface DoesNotExist : SameFileError, IjentFsError.DoesNotExist
    interface PermissionDenied : SameFileError, IjentFsError.PermissionDenied
    interface NotDirectory : SameFileError, IjentFsError.NotDirectory
    interface NotFile : SameFileError, IjentFsError.NotFile
    interface Other : SameFileError, IjentFsError.Other
  }

  suspend fun fileReader(path: IjentPath.Absolute): IjentFsResult<
    IjentOpenedFile.Reader,
    FileReaderError>

  sealed interface FileReaderError : IjentFsError {
    interface DoesNotExist : FileReaderError, IjentFsError.DoesNotExist
    interface PermissionDenied : FileReaderError, IjentFsError.PermissionDenied
    interface NotDirectory : FileReaderError, IjentFsError.NotDirectory
    interface NotFile : FileReaderError, IjentFsError.NotFile
    interface Other : FileReaderError, IjentFsError.Other
  }

  /**
   * If [append] is false, the file is erased.
   */
  suspend fun fileWriter(
    path: IjentPath.Absolute,
    append: Boolean = false,
    creationMode: FileWriterCreationMode = FileWriterCreationMode.ALLOW_CREATE,
  ): IjentFsResult<
    IjentOpenedFile.Writer,
    FileWriterError>

  sealed interface FileWriterError : IjentFsError {
    interface DoesNotExist : FileWriterError, IjentFsError.DoesNotExist
    interface PermissionDenied : FileWriterError, IjentFsError.PermissionDenied
    interface NotDirectory : FileWriterError, IjentFsError.NotDirectory
    interface NotFile : FileWriterError, IjentFsError.NotFile
    interface Other : FileWriterError, IjentFsError.Other
  }

  enum class FileWriterCreationMode {
    ALLOW_CREATE, ONLY_CREATE, ONLY_OPEN_EXISTING,
  }
}

sealed interface IjentOpenedFile {
  val path: IjentPath.Absolute

  @Throws(CloseException::class)
  suspend fun close()

  sealed class CloseException(
    where: IjentPath.Absolute,
    additionalMessage: @NlsSafe String,
  ) : IjentFsIOException(where, additionalMessage) {
    class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String)
      : CloseException(where, additionalMessage), IjentFsError.Other
  }

  fun tell(): IjentFsResult<
    Long,
    TellError>

  sealed interface TellError : IjentFsError {
    interface Other : TellError, IjentFsError.Other
  }

  suspend fun seek(offset: Long, whence: SeekWhence): IjentFsResult<
    Long,
    SeekError>

  sealed interface SeekError : IjentFsError {
    interface InvalidValue : SeekError, IjentFsError
    interface Other : SeekError, IjentFsError.Other
  }

  enum class SeekWhence {
    START, CURRENT, END,
  }

  interface Reader : IjentOpenedFile {
    suspend fun read(buf: ByteBuffer): IjentFsResult<
      Int,
      ReadError>

    sealed interface ReadError : IjentFsError {
      interface InvalidValue : ReadError, IjentFsError
      interface Other : ReadError, IjentFsError.Other
    }
  }

  interface Writer : IjentOpenedFile {
    suspend fun write(buf: ByteBuffer): IjentFsResult<
      Int,
      WriteError>

    sealed interface WriteError : IjentFsError {
      sealed interface ResourceExhausted : WriteError, IjentFsError.Other {
        interface DiskQuotaExceeded : ResourceExhausted, IjentFsError.Other
        interface FileSizeExceeded : ResourceExhausted, IjentFsError.Other
        interface NoSpaceLeft : ResourceExhausted, IjentFsError.Other
      }
      interface Other : WriteError, IjentFsError.Other
    }

    @Throws(FlushException::class)
    suspend fun flush()

    sealed class FlushException(
      where: IjentPath.Absolute,
      additionalMessage: @NlsSafe String,
    ) : IjentFsIOException(where, additionalMessage) {
      class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String)
        : FlushException(where, additionalMessage), IjentFsError.Other
    }

    @Throws(TruncateException::class)
    suspend fun truncate()

    sealed class TruncateException(
      where: IjentPath.Absolute,
      additionalMessage: @NlsSafe String,
    ) : IjentFsIOException(where, additionalMessage) {
      class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String)
        : TruncateException(where, additionalMessage), IjentFsError.Other
    }
  }
}

interface IjentFileSystemPosixApi : IjentFileSystemApi {
  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFsResult<
    Collection<Pair<String, IjentPosixFileInfo>>,
    IjentFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): IjentFsResult<
    IjentPosixFileInfo,
    IjentFileSystemApi.StatError>
}

interface IjentFileSystemWindowsApi : IjentFileSystemApi {
  suspend fun getRootDirectories(): Collection<IjentPath.Absolute>

  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFsResult<
    Collection<Pair<String, IjentWindowsFileInfo>>,
    IjentFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): IjentFsResult<
    IjentWindowsFileInfo,
    IjentFileSystemApi.StatError>
}