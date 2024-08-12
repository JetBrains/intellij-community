// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ijent.*
import kotlinx.coroutines.CoroutineScope
import java.nio.ByteBuffer

// TODO Integrate case-(in)sensitiveness into the interface.

sealed interface IjentFileSystemApi {
  /**
   * The same [CoroutineScope] as in the corresponding [com.intellij.platform.ijent.IjentApi].
   */
  @Deprecated("API should avoid exposing coroutine scopes")
  val coroutineScope: CoroutineScope

  /**
   * The same as the user from [com.intellij.platform.ijent.IjentApi.info].
   *
   * There's a duplication of methods because [user] is required for checking file permissions correctly, but also it can be required
   * in other cases outside the filesystem.
   *
   * TODO If `user` is non-suspendable, then `userHome` should be non-suspendable too. Or not?
   */
  val user: IjentInfo.User

  /**
   * A user may have no home directory on Unix-like systems, for example, the user `nobody`.
   */
  @Throws(IjentUnavailableException::class)
  suspend fun userHome(): IjentPath.Absolute?

  /**
   * Returns names of files in a directory. If [path] is a symlink, it will be resolved, but no symlinks are resolved among children.
   */
  @Throws(IjentUnavailableException::class)
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
  @Throws(IjentUnavailableException::class)
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
  @Throws(IjentUnavailableException::class)
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
  @Throws(IjentUnavailableException::class)
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
  @Throws(IjentUnavailableException::class)
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

  /**
   * Opens file only for reading
   */
  @Throws(IjentUnavailableException::class)
  suspend fun openForReading(path: IjentPath.Absolute): IjentFsResult<
    IjentOpenedFile.Reader,
    FileReaderError>

  sealed interface FileReaderError : IjentFsError {
    interface AlreadyExists : FileReaderError, IjentFsError.AlreadyExists
    interface DoesNotExist : FileReaderError, IjentFsError.DoesNotExist
    interface PermissionDenied : FileReaderError, IjentFsError.PermissionDenied
    interface NotDirectory : FileReaderError, IjentFsError.NotDirectory
    interface NotFile : FileReaderError, IjentFsError.NotFile
    interface Other : FileReaderError, IjentFsError.Other
  }

  /**
   * Opens file only for writing
   */
  @Throws(IjentUnavailableException::class)
  suspend fun openForWriting(
    options: WriteOptions,
  ): IjentFsResult<
    IjentOpenedFile.Writer,
    FileWriterError>

  sealed interface WriteOptions {
    val path: IjentPath.Absolute

    /**
     * Whether to append new data to the end of file.
     * Default: `false`
     */
    fun append(v: Boolean): WriteOptions
    val append: Boolean

    /**
     * Whether to remove contents from the existing file.
     * Default: `false`
     */
    fun truncateExisting(v: Boolean): WriteOptions
    val truncateExisting: Boolean

    /**
     * Defines the behavior if the written file does not exist
     * Default: [FileWriterCreationMode.ONLY_OPEN_EXISTING]
     */
    fun creationMode(v: FileWriterCreationMode): WriteOptions
    val creationMode: FileWriterCreationMode
  }

  enum class FileWriterCreationMode {
    ALLOW_CREATE, ONLY_CREATE, ONLY_OPEN_EXISTING,
  }

  sealed interface FileWriterError : IjentFsError {
    interface DoesNotExist : FileWriterError, IjentFsError.DoesNotExist
    interface AlreadyExists : FileWriterError, IjentFsError.AlreadyExists
    interface PermissionDenied : FileWriterError, IjentFsError.PermissionDenied
    interface NotDirectory : FileWriterError, IjentFsError.NotDirectory
    interface NotFile : FileWriterError, IjentFsError.NotFile
    interface Other : FileWriterError, IjentFsError.Other
  }

  @Throws(IjentUnavailableException::class)
  suspend fun openForReadingAndWriting(options: WriteOptions): IjentFsResult<IjentOpenedFile.ReaderWriter, FileWriterError>


  @Throws(DeleteException::class, IjentUnavailableException::class)
  suspend fun delete(path: IjentPath.Absolute, removeContent: Boolean, followLinks: Boolean)

  sealed class DeleteException(
    where: IjentPath.Absolute,
    additionalMessage: @NlsSafe String,
  ) : IjentFsIOException(where, additionalMessage) {
    class DoesNotExist(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : DeleteException(where, additionalMessage), IjentFsError.DoesNotExist
    class DirNotEmpty(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : DeleteException(where, additionalMessage), IjentFsError.DirNotEmpty
    class PermissionDenied(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : DeleteException(where, additionalMessage), IjentFsError.PermissionDenied

    /**
     * Thrown only when `followLinks` is specified for [delete]
     */
    class UnresolvedLink(where: IjentPath.Absolute): DeleteException(where, "Attempted to delete a file referenced by an unresolvable link")
    class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String)
      : DeleteException(where, additionalMessage), IjentFsError.Other
  }

  @Throws(CopyException::class, IjentUnavailableException::class)
  suspend fun copy(options: CopyOptions)

  sealed interface CopyOptions {
    val source: IjentPath.Absolute
    val target: IjentPath.Absolute

    /**
     * Relevant for copying directories.
     * [shouldCopyRecursively] indicates whether the directory should be copied recirsively.
     * If `false`, then only the directory itself is copied, resulting in an empty directory located at target path
     */
    fun copyRecursively(v: Boolean): CopyOptions
    val copyRecursively: Boolean

    fun replaceExisting(v: Boolean): CopyOptions
    val replaceExisting: Boolean

    fun preserveAttributes(v: Boolean): CopyOptions
    val preserveAttributes: Boolean

    fun interruptible(v: Boolean): CopyOptions
    val interruptible: Boolean

    fun followLinks(v: Boolean): CopyOptions
    val followLinks: Boolean
  }

  sealed class CopyException(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : IjentFsIOException(where, additionalMessage) {
    class SourceDoesNotExist(where: IjentPath.Absolute) : CopyException(where, "Source does not exist"), IjentFsError.DoesNotExist
    class TargetAlreadyExists(where: IjentPath.Absolute) : CopyException(where, "Target already exists"), IjentFsError.AlreadyExists
    class PermissionDenied(where: IjentPath.Absolute) : CopyException(where, "Permission denied"), IjentFsError.PermissionDenied
    class NotEnoughSpace(where: IjentPath.Absolute) : CopyException(where, "Not enough space"), IjentFsError.NotEnoughSpace
    class NameTooLong(where: IjentPath.Absolute) : CopyException(where, "Name too long"), IjentFsError.NameTooLong
    class ReadOnlyFileSystem(where: IjentPath.Absolute) : CopyException(where, "File system is read-only"), IjentFsError.ReadOnlyFileSystem
    class FileSystemError(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CopyException(where, additionalMessage), IjentFsError.Other
    class TargetDirNotEmpty(where: IjentPath.Absolute) : CopyException(where, "Target directory is not empty"), IjentFsError.DirNotEmpty
    class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CopyException(where, additionalMessage), IjentFsError.Other
  }

  @Throws(MoveException::class)
  suspend fun move(source: IjentPath.Absolute, target: IjentPath.Absolute, replaceExisting: Boolean, followLinks: Boolean)

  sealed class MoveException(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : IjentFsIOException(where, additionalMessage) {
    class SourceDoesNotExist(where: IjentPath.Absolute) : MoveException(where, "Source does not exist"), IjentFsError.DoesNotExist
    class TargetAlreadyExists(where: IjentPath.Absolute) : MoveException(where, "Target already exists"), IjentFsError.AlreadyExists
    class PermissionDenied(where: IjentPath.Absolute) : MoveException(where, "Permission denied"), IjentFsError.PermissionDenied
    class NameTooLong(where: IjentPath.Absolute) : MoveException(where, "Name too long"), IjentFsError.NameTooLong
    class ReadOnlyFileSystem(where: IjentPath.Absolute) : MoveException(where, "File system is read-only"), IjentFsError.ReadOnlyFileSystem
    class FileSystemError(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : MoveException(where, additionalMessage), IjentFsError.Other
    class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : MoveException(where, additionalMessage), IjentFsError.Other
  }

  companion object Arguments {
    @JvmStatic
    fun writeOptionsBuilder(path: IjentPath.Absolute): WriteOptions =
      WriteOptionsImpl(path)

    @JvmStatic
    fun copyOptionsBuilder(source: IjentPath.Absolute, target: IjentPath.Absolute): CopyOptions =
      CopyOptionsImpl(source, target)
  }
}

sealed interface IjentOpenedFile {
  val path: IjentPath.Absolute

  @Throws(CloseException::class, IjentUnavailableException::class)
  suspend fun close()

  sealed class CloseException(
    where: IjentPath.Absolute,
    additionalMessage: @NlsSafe String,
  ) : IjentFsIOException(where, additionalMessage) {
    class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String)
      : CloseException(where, additionalMessage), IjentFsError.Other
  }

  @Throws(IjentUnavailableException::class)
  suspend fun tell(): IjentFsResult<
    Long,
    TellError>

  sealed interface TellError : IjentFsError {
    interface Other : TellError, IjentFsError.Other
  }

  @Throws(IjentUnavailableException::class)
  suspend fun seek(offset: Long, whence: SeekWhence): IjentFsResult<
    Long,
    SeekError>

  sealed interface SeekError : IjentFsError {
    interface InvalidValue : SeekError, IjentFsError
    interface UnknownFile : SeekError, IjentFsError.UnknownFile
    interface Other : SeekError, IjentFsError.Other
  }

  enum class SeekWhence {
    START, CURRENT, END,
  }


  interface Reader : IjentOpenedFile {

    /**
     * Reads data from the current position of the file (see [tell])
     *
     * If the remote file is read completely, then this function returns [ReadResult] with [ReadResult.EOF].
     * Otherwise, if there are any data left to read, then it returns [ReadResult.Bytes].
     * Note, that [ReadResult.Bytes] can be `0` if [buf] cannot accept new data.
     *
     * This operation modifies the file's cursor, i.e. [tell] may show different results before and after this function is invoked.
     *
     * It reads not more than [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
     */
    @Throws(IjentUnavailableException::class)
    suspend fun read(buf: ByteBuffer): IjentFsResult<ReadResult, ReadError>

    /**
     * Reads data from the position [offset] of the file.
     *
     * This operation does not modify the file's cursor, i.e. [tell] will show the same result before and after this function is invoked.
     *
     * It reads not more than [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
     */
    @Throws(IjentUnavailableException::class)
    suspend fun read(buf: ByteBuffer, offset: Long): IjentFsResult<ReadResult, ReadError>

    sealed interface ReadResult {
      interface EOF : ReadResult
      interface Bytes : ReadResult {
        val bytesRead: Int
      }
    }

    sealed interface ReadError : IjentFsError {
      interface UnknownFile : ReadError, IjentFsError.UnknownFile
      interface InvalidValue : ReadError, IjentFsError
      interface Other : ReadError, IjentFsError.Other
    }
  }

  interface Writer : IjentOpenedFile {
    /**
     * TODO Document
     *
     * It writes not more than [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
     */
    @Throws(IjentUnavailableException::class)
    suspend fun write(buf: ByteBuffer): IjentFsResult<
      Int,
      WriteError>

    /**
     * TODO Document
     *
     * It writes not more than [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
     */
    @Throws(IjentUnavailableException::class)
    suspend fun write(buf: ByteBuffer, pos: Long): IjentFsResult<
      Int,
      WriteError>

    sealed interface WriteError : IjentFsError {
      interface InvalidValue : WriteError, IjentFsError
      sealed interface ResourceExhausted : WriteError, IjentFsError.Other {
        interface DiskQuotaExceeded : ResourceExhausted, IjentFsError.Other
        interface FileSizeExceeded : ResourceExhausted, IjentFsError.Other
        interface NoSpaceLeft : ResourceExhausted, IjentFsError.Other
      }

      interface UnknownFile : WriteError, IjentFsError.UnknownFile
      interface Other : WriteError, IjentFsError.Other
    }

    @Throws(FlushException::class, IjentUnavailableException::class)
    suspend fun flush()

    sealed class FlushException(
      where: IjentPath.Absolute,
      additionalMessage: @NlsSafe String,
    ) : IjentFsIOException(where, additionalMessage) {
      class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String)
        : FlushException(where, additionalMessage), IjentFsError.Other
    }

    @Throws(TruncateException::class, IjentUnavailableException::class)
    suspend fun truncate(size: Long)

    sealed class TruncateException(
      where: IjentPath.Absolute,
      additionalMessage: @NlsSafe String,
    ) : IjentFsIOException(where, additionalMessage) {
      class UnknownFile(where: IjentPath.Absolute) : TruncateException(where, "Could not find opened file"), IjentFsError.UnknownFile
      class NegativeOffset(where: IjentPath.Absolute, offset: Long) : TruncateException(where, "Offset $offset is negative")
      class OffsetTooBig(where: IjentPath.Absolute, offset: Long) : TruncateException(where, "Offset $offset is too big for truncation")
      class ReadOnlyFs(where: IjentPath.Absolute) : TruncateException(where, "File system is read-only"), IjentFsError.ReadOnlyFileSystem
      class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String)
        : TruncateException(where, additionalMessage), IjentFsError.Other
    }
  }

  interface ReaderWriter : Reader, Writer
}

interface IjentFileSystemPosixApi : IjentFileSystemApi {
  override val user: IjentPosixInfo.User

  enum class CreateDirAttributePosix {
    // todo
  }

  @Throws(CreateDirectoryException::class, IjentUnavailableException::class)
  suspend fun createDirectory(path: IjentPath.Absolute, attributes: List<CreateDirAttributePosix>)

  sealed class CreateDirectoryException(
    where: IjentPath.Absolute,
    additionalMessage: @NlsSafe String,
  ) : IjentFsIOException(where, additionalMessage) {
    class DirAlreadyExists(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateDirectoryException(where, additionalMessage), IjentFsError.AlreadyExists
    class FileAlreadyExists(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateDirectoryException(where, additionalMessage), IjentFsError.AlreadyExists
    class ParentNotFound(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateDirectoryException(where, additionalMessage), IjentFsError.DoesNotExist
    class PermissionDenied(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateDirectoryException(where, additionalMessage), IjentFsError.PermissionDenied
    class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateDirectoryException(where, additionalMessage), IjentFsError.Other
  }

  @Throws(IjentUnavailableException::class)
  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFsResult<
    Collection<Pair<String, IjentPosixFileInfo>>,
    IjentFileSystemApi.ListDirectoryError>

  @Throws(IjentUnavailableException::class)
  override suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): IjentFsResult<
    IjentPosixFileInfo,
    IjentFileSystemApi.StatError>

  /**
   * Notice that the first argument is the target of the symlink,
   * like in `ln -s` tool, like in `symlink(2)` from LibC, but opposite to `java.nio.file.spi.FileSystemProvider.createSymbolicLink`.
   */
  @Throws(CreateSymbolicLinkException::class, IjentUnavailableException::class)
  suspend fun createSymbolicLink(target: IjentPath, linkPath: IjentPath.Absolute)

  sealed class CreateSymbolicLinkException(
    where: IjentPath.Absolute,
    additionalMessage: @NlsSafe String,
  ) : IjentFsIOException(where, additionalMessage) {
    /**
     * Example: `createSymbolicLink("anywhere", "/directory_that_does_not_exist")`
     */
    class DoesNotExist(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateSymbolicLinkException(where, additionalMessage), IjentFsError.DoesNotExist

    /**
     * Examples:
     * * `createSymbolicLink("anywhere", "/etc/passwd")`
     * * `createSymbolicLink("anywhere", "/home")`
     */
    class FileAlreadyExists(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateSymbolicLinkException(where, additionalMessage), IjentFsError.AlreadyExists

    /**
     * Example: `createSymbolicLink("anywhere", "/etc/passwd/oops")`
     */
    class NotDirectory(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateSymbolicLinkException(where, additionalMessage), IjentFsError.NotDirectory

    /**
     * Example:
     * * With non-root permissions: `createSymbolicLink("anywhere", "/root/oops")`
     */
    class PermissionDenied(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateSymbolicLinkException(where, additionalMessage), IjentFsError.PermissionDenied

    /**
     * Everything else, including `ELOOP`.
     * Despite an allegedly related name, the errno `ELOOP` has nothing to do with symlinks creation,
     * and it can appear only in this case:
     * ```
     * createSymbolicLink("/tmp/foobar", "/tmp/foobar") // OK
     * createSymbolicLink("anywhere", "/tmp/foobar/oops") // Other("something about ELOOP")
     * ```
     */
    class Other(where: IjentPath.Absolute, additionalMessage: @NlsSafe String) : CreateSymbolicLinkException(where, additionalMessage), IjentFsError.Other
  }
}

interface IjentFileSystemWindowsApi : IjentFileSystemApi {
  override val user: IjentWindowsInfo.User

  @Throws(IjentUnavailableException::class)
  suspend fun getRootDirectories(): Collection<IjentPath.Absolute>

  @Throws(IjentUnavailableException::class)
  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFsResult<
    Collection<Pair<String, IjentWindowsFileInfo>>,
    IjentFileSystemApi.ListDirectoryError>

  @Throws(IjentUnavailableException::class)
  override suspend fun stat(path: IjentPath.Absolute, resolveSymlinks: Boolean): IjentFsResult<
    IjentWindowsFileInfo,
    IjentFileSystemApi.StatError>
}