// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.ijent.IjentId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

// TODO Integrate case-(in)sensitiveness into the interface.

/**
 * No strict reason for having a separate interface for this. It's split just for not making [com.intellij.platform.ijent.IjentApi] too big.
 *
 * This class is significantly inspired by `fleet.api.FsApi` and related classes. Most names remain the same.
 */
@ApiStatus.Experimental
interface IjentFileSystemApi {
  /**
   * The same [IjentId] as in the corresponding [com.intellij.platform.ijent.IjentApi].
   */
  val id: IjentId

  /**
   * The same [CoroutineScope] as in the corresponding [com.intellij.platform.ijent.IjentApi].
   */
  val coroutineScope: CoroutineScope

  /**
   * Returns true if IJent runs on Windows, false if on a Unix-like OS.
   */
  val isWindows: Boolean

  /**
   * Returns `/` on Unix-like and disk labels on Windows.
   */
  suspend fun getRootDirectories(): Collection<IjentPath>

  // TODO
  //suspend fun createTempDir(): IjentRemotePath

  /**
   * A user may have no home directory on Unix-like systems, for example, the user `nobody`.
   */
  suspend fun userHome(): IjentPath?

  /**
   * Returns names of files in a directory. If [path] is a symlink, it will be resolved, but no symlinks are resolved among children.
   */
  suspend fun listDirectory(path: IjentPath): IjentFsResult<Collection<String>, ListDirectoryError>

  /**
   * Returns names of files in a directory. If [path] is a symlink, it will be resolved regardless of [resolveSymlinks].
   *  TODO Is it an expected behaviour?
   *
   * [resolveSymlinks] controls resolution of symlinks among children.
   *  TODO The behaviour is different from resolveSymlinks in [stat]. To be fixed.
   */
  suspend fun listDirectoryWithAttrs(path: IjentPath, resolveSymlinks: Boolean = true): IjentFsResult<FileInfo, ListDirectoryError>

  sealed interface ListDirectoryError : IjentFsResultError {
    data object NotDirectory : ListDirectoryError, IjentFsResultError
    data class Generic(val generic: IjentFsResultError.Generic) : ListDirectoryError, IjentFsResultError by generic
  }

  data class FileInfo(
    val path: IjentPath,
    val fileType: Type,
    //val permissions: Permissions,  // TODO There are too many options for a good API. Let's add them as soon as they're needed.
  ) {
    sealed interface Type {
      data object Directory : Type
      data object Regular : Type

      sealed interface Symlink : Type {
        data object Unresolved : Symlink
        data class Resolved(val result: IjentPath) : Symlink
      }

      data object Other : Type
    }
  }

  /**
   * Resolves all symlinks in the path. Corresponds to realpath(3) on Unix and GetFinalPathNameByHandle on Windows.
   */
  suspend fun canonicalize(path: IjentPath): IjentFsResult<IjentPath, IjentFsResultError.Generic>

  /**
   * Similar to stat(2) and lstat(2). [resolveSymlinks] has an impact only on [FileInfo.fileType] if [path] points on a symlink.
   */
  suspend fun stat(path: IjentPath, resolveSymlinks: Boolean): IjentFsResult<FileInfo, IjentFsResultError.Generic>

  // TODO Is it needed?
  // suspend fun fileHash(path: IjentRemotePath): RpcResult<Blob, IOError>

  // TODO
  //suspend fun setExecutable(path: IjentRemotePath, executable: Boolean): RpcResult<Unit, IOError>

  // TODO
  /**
   * Succeeds only if new file was created
   */
  //suspend fun createFile(path: IjentRemotePath, byteStream: ReceiveChannel<Blob>? = null,
  //                       completion: SendChannel<RpcResult<Unit, IOError>>)

  // TODO
  /**
   * Recursively create a directory and all of its parent components if they are missing.
   */
  //suspend fun createDirectoryAll(path: IjentRemotePath): RpcResult<Unit, IOError>

  // TODO
  /**
   * Delete a file or a directory recursively
   */
  //suspend fun delete(path: IjentRemotePath, recursive: Boolean): RpcResult<Unit, IOError>

  // TODO It's canonicalize, isn't it?
  //suspend fun readSymlink(path: IjentRemotePath): RpcResult<IjentRemotePath, IOError>

  // TODO
  /**
   * Move/rename a file to a new location.
   *
   * The behaviour of the operation when the target file already exists is controlled
   * by the openMode parameter.
   * If OpenMode.New is specified, the operation will only succeed if target file
   * doesn't exist. OpenMode.Existing will only succeed if the target file exists.
   * OpenMode.Create will overwrite the file if it exists and create a new one if it doesn't.
   */
  //suspend fun moveFile(source: IjentRemotePath, target: IjentRemotePath, openMode: OpenMode): RpcResult<Unit, IOError>

  /**
   * on Unix return true if both paths have the same inode.
   * On Windows some heuristics are used, for more details see https://docs.rs/same-file/1.0.6/same_file/
   */
  suspend fun sameFile(source: IjentPath, target: IjentPath): IjentFsResult<Boolean, SameFileError>

  data class SameFileError(val path: IjentFsResultError, val error: IjentFsResultError.Generic) : IjentFsResultError {
    override fun toString(): String = "$path: $error"
  }

  // TODO
  ///**
  // * Copy a file to a new location.
  // *
  // * The behaviour of the operation when the target file already exists is consistent
  // * with `moveFile`
  // *
  // * @see moveFile
  // */
  //suspend fun copyFile(source: IjentRemotePath, target: IjentRemotePath, openMode: OpenMode): RpcResult<Unit, IOError>

  // TODO
  //suspend fun copyDirectory(source: IjentRemotePath, target: IjentRemotePath): RpcResult<Unit, IOError>

  // TODO
  //suspend fun readFile(path: IjentRemotePath, byteStream: SendChannel<RpcResult<Blob, IOError>>): RpcResult<Unit, IOError>

  // TODO
  //suspend fun readFileWithAttributes(path: IjentRemotePath,
  //                                   byteStream: SendChannel<RpcResult<Blob, IOError>>): RpcResult<FileAttributes, IOError>

  // TODO
  //suspend fun writeFile(path: IjentRemotePath,
  //                      byteStream: ReceiveChannel<Blob>,
  //                      expectedHash: Blob? = null,
  //                      openMode: OpenMode = OpenMode.Existing,
  //                      completion: SendChannel<RpcResult<FileHash, IOError>>)

  // TODO
  //suspend fun detectCharset(path: IjentRemotePath): RpcResult<DetectedCharset, IOError>
}

interface IjentFsResultError {
  override fun toString(): String

  sealed interface Generic : IjentFsResultError {
    data object DoesNotExist : Generic

    /**
     * If IJent tries to list the directory "/root/foo/bar", [where] will point on "/root" as on the main problematic path.
     *  TODO Implement that.
     */
    data class PermissionDenied(val where: IjentPath) : Generic
  }
}

sealed interface IjentFsResult<T, E : IjentFsResultError> {
  data class Ok<T, E : IjentFsResultError>(val result: T) : IjentFsResult<T, E>
  data class Err<T, E : IjentFsResultError>(val error: E, val message: String) : IjentFsResult<T, E>
}