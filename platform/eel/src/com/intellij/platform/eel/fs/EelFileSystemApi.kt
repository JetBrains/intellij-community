// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelUserInfo
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.EelUserWindowsInfo
import com.intellij.platform.eel.fs.EelFileSystemApi.StatError
import com.intellij.platform.eel.path.EelPath
import java.nio.ByteBuffer
import kotlin.Throws

val EelFileSystemApi.pathOs: EelPath.Absolute.OS
  get() = when (this) {
    is EelFileSystemPosixApi -> EelPath.Absolute.OS.UNIX
    is EelFileSystemWindowsApi -> EelPath.Absolute.OS.WINDOWS
    else -> throw UnsupportedOperationException("Unsupported OS: ${this::class.java}")
  }

val EelFileSystemApi.pathSeparator: String
  get() = when (this) {
    is EelFileSystemPosixApi -> ":"
    is EelFileSystemWindowsApi -> ";"
    else -> throw UnsupportedOperationException("Unsupported OS: ${this::class.java}")
  }

fun EelFileSystemApi.getPath(string: String, vararg other: String): EelPath.Absolute {
  return EelPath.Absolute.parse(pathOs, string, *other)
}

// TODO Integrate case-(in)sensitiveness into the interface.

interface EelFileSystemApi {

  /**
   * There's a duplication of methods because [user] is required for checking file permissions correctly, but also it can be required
   * in other cases outside the filesystem.
   */
  val user: EelUserInfo

  /**
   * Returns names of files in a directory. If [path] is a symlink, it will be resolved, but no symlinks are resolved among children.
   */
  suspend fun listDirectory(path: EelPath.Absolute): EelResult<
    Collection<String>,
    ListDirectoryError>

  /**
   * Returns names of files in a directory and the attributes of the corresponding files.
   * If [path] is a symlink, it will be resolved regardless of [symlinkPolicy].
   *  TODO Is it an expected behaviour?
   *
   * [symlinkPolicy] controls resolution of symlinks among children.
   *  TODO The behaviour is different from resolveSymlinks in [stat]. To be fixed.
   */
  suspend fun listDirectoryWithAttrs(
    path: EelPath.Absolute,
    symlinkPolicy: SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelFileInfo>>,
    ListDirectoryError>

  @Suppress("unused")
  sealed interface ListDirectoryError : EelFsError {
    interface DoesNotExist : ListDirectoryError, EelFsError.DoesNotExist
    interface PermissionDenied : ListDirectoryError, EelFsError.PermissionDenied
    interface NotDirectory : ListDirectoryError, EelFsError.NotDirectory
    interface Other : ListDirectoryError, EelFsError.Other
  }

  /**
   * Resolves all symlinks in the path. Corresponds to realpath(3) on Unix and GetFinalPathNameByHandle on Windows.
   */
  suspend fun canonicalize(path: EelPath.Absolute): EelResult<
    EelPath.Absolute,
    CanonicalizeError>

  sealed interface CanonicalizeError : EelFsError {
    interface DoesNotExist : CanonicalizeError, EelFsError.DoesNotExist
    interface PermissionDenied : CanonicalizeError, EelFsError.PermissionDenied
    interface NotDirectory : CanonicalizeError, EelFsError.NotDirectory
    interface NotFile : CanonicalizeError, EelFsError.NotFile
    interface Other : CanonicalizeError, EelFsError.Other
  }

  /**
   * Similar to stat(2) and lstat(2). [symlinkPolicy] has an impact only on [EelFileInfo.type] if [path] points on a symlink.
   */
  suspend fun stat(path: EelPath.Absolute, symlinkPolicy: SymlinkPolicy): EelResult<EelFileInfo, StatError>

  /**
   * Defines the behavior of FS operations on symbolic links
   */
  enum class SymlinkPolicy {
    /**
     * Leaves symlinks unresolved.
     * This option makes the operation a bit more efficient if it is not interested in symlinks.
     */
    DO_NOT_RESOLVE,

    /**
     * Resolves a symlink and returns the information about the target of the symlink,
     * But does not perform anything on the target of the symlink itself.
     */
    JUST_RESOLVE,

    /**
     * Resolves a symlink, follows it, and performs the required operation on target.
     */
    RESOLVE_AND_FOLLOW,
  }

  sealed interface StatError : EelFsError {
    interface DoesNotExist : StatError, EelFsError.DoesNotExist
    interface PermissionDenied : StatError, EelFsError.PermissionDenied
    interface NotDirectory : StatError, EelFsError.NotDirectory
    interface NotFile : StatError, EelFsError.NotFile
    interface Other : StatError, EelFsError.Other
  }

  /**
   * On Unix return true if both paths have the same inode.
   * On Windows some heuristics are used, for more details see https://docs.rs/same-file/1.0.6/same_file/
   */
  suspend fun sameFile(source: EelPath.Absolute, target: EelPath.Absolute): EelResult<
    Boolean,
    SameFileError>

  sealed interface SameFileError : EelFsError {
    interface DoesNotExist : SameFileError, EelFsError.DoesNotExist
    interface PermissionDenied : SameFileError, EelFsError.PermissionDenied
    interface NotDirectory : SameFileError, EelFsError.NotDirectory
    interface NotFile : SameFileError, EelFsError.NotFile
    interface Other : SameFileError, EelFsError.Other
  }

  /**
   * Opens the file only for reading
   */
  suspend fun openForReading(path: EelPath.Absolute): EelResult<
    EelOpenedFile.Reader,
    FileReaderError>

  sealed interface FileReaderError : EelFsError {
    interface AlreadyExists : FileReaderError, EelFsError.AlreadyExists
    interface DoesNotExist : FileReaderError, EelFsError.DoesNotExist
    interface PermissionDenied : FileReaderError, EelFsError.PermissionDenied
    interface NotDirectory : FileReaderError, EelFsError.NotDirectory
    interface NotFile : FileReaderError, EelFsError.NotFile
    interface Other : FileReaderError, EelFsError.Other
  }

  /**
   * Opens the file only for writing
   */
  suspend fun openForWriting(
    options: WriteOptions,
  ): EelResult<
    EelOpenedFile.Writer,
    FileWriterError>

  sealed interface WriteOptions {
    val path: EelPath.Absolute

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

  sealed interface FileWriterError : EelFsError {
    interface DoesNotExist : FileWriterError, EelFsError.DoesNotExist
    interface AlreadyExists : FileWriterError, EelFsError.AlreadyExists
    interface PermissionDenied : FileWriterError, EelFsError.PermissionDenied
    interface NotDirectory : FileWriterError, EelFsError.NotDirectory
    interface NotFile : FileWriterError, EelFsError.NotFile
    interface Other : FileWriterError, EelFsError.Other
  }

  suspend fun openForReadingAndWriting(options: WriteOptions): EelResult<EelOpenedFile.ReaderWriter, FileWriterError>

  @Throws(DeleteException::class)
  suspend fun delete(path: EelPath.Absolute, removeContent: Boolean)

  sealed class DeleteException(
    where: EelPath.Absolute,
    additionalMessage: String,
  ) : EelFsIOException(where, additionalMessage) {
    class DoesNotExist(where: EelPath.Absolute, additionalMessage: String) : DeleteException(where,
                                                                                             additionalMessage), EelFsError.DoesNotExist

    class DirNotEmpty(where: EelPath.Absolute, additionalMessage: String) : DeleteException(where,
                                                                                            additionalMessage), EelFsError.DirNotEmpty

    class PermissionDenied(where: EelPath.Absolute, additionalMessage: String) : DeleteException(where,
                                                                                                 additionalMessage), EelFsError.PermissionDenied

    /**
     * Thrown only when `followLinks` is specified for [delete]
     */
    class UnresolvedLink(where: EelPath.Absolute) : DeleteException(where, "Attempted to delete a file referenced by an unresolvable link")
    class Other(where: EelPath.Absolute, additionalMessage: String)
      : DeleteException(where, additionalMessage), EelFsError.Other
  }

  @Throws(CopyException::class)
  suspend fun copy(options: CopyOptions)

  sealed interface CopyOptions {
    val source: EelPath.Absolute
    val target: EelPath.Absolute

    /**
     * Relevant for copying directories.
     * [copyRecursively] indicates whether the directory should be copied recursively.
     * If `false`, then only the directory itself is copied, resulting in an empty directory located at the target path
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

  sealed class CopyException(where: EelPath.Absolute, additionalMessage: String) : EelFsIOException(where, additionalMessage) {
    class SourceDoesNotExist(where: EelPath.Absolute) : CopyException(where, "Source does not exist"), EelFsError.DoesNotExist
    class TargetAlreadyExists(where: EelPath.Absolute) : CopyException(where, "Target already exists"), EelFsError.AlreadyExists
    class PermissionDenied(where: EelPath.Absolute) : CopyException(where, "Permission denied"), EelFsError.PermissionDenied
    class NotEnoughSpace(where: EelPath.Absolute) : CopyException(where, "Not enough space"), EelFsError.NotEnoughSpace
    class NameTooLong(where: EelPath.Absolute) : CopyException(where, "Name too long"), EelFsError.NameTooLong
    class ReadOnlyFileSystem(where: EelPath.Absolute) : CopyException(where, "File system is read-only"), EelFsError.ReadOnlyFileSystem
    class FileSystemError(where: EelPath.Absolute, additionalMessage: String) : CopyException(where, additionalMessage), EelFsError.Other
    class TargetDirNotEmpty(where: EelPath.Absolute) : CopyException(where, "Target directory is not empty"), EelFsError.DirNotEmpty
    class Other(where: EelPath.Absolute, additionalMessage: String) : CopyException(where, additionalMessage), EelFsError.Other
  }

  enum class ReplaceExistingDuringMove {
    REPLACE_EVERYTHING,

    /** For compatibility with Java NIO. */
    DO_NOT_REPLACE_DIRECTORIES,

    DO_NOT_REPLACE,
  }

  @Throws(MoveException::class)
  suspend fun move(source: EelPath.Absolute, target: EelPath.Absolute, replaceExisting: ReplaceExistingDuringMove, followLinks: Boolean)

  sealed class MoveException(where: EelPath.Absolute, additionalMessage: String) : EelFsIOException(where, additionalMessage) {
    class SourceDoesNotExist(where: EelPath.Absolute) : MoveException(where, "Source does not exist"), EelFsError.DoesNotExist
    class TargetAlreadyExists(where: EelPath.Absolute) : MoveException(where, "Target already exists"), EelFsError.AlreadyExists
    class TargetIsDirectory(where: EelPath.Absolute) : MoveException(where, "Target already exists and it is a directory"), EelFsError.AlreadyExists
    class PermissionDenied(where: EelPath.Absolute) : MoveException(where, "Permission denied"), EelFsError.PermissionDenied
    class NameTooLong(where: EelPath.Absolute) : MoveException(where, "Name too long"), EelFsError.NameTooLong
    class ReadOnlyFileSystem(where: EelPath.Absolute) : MoveException(where, "File system is read-only"), EelFsError.ReadOnlyFileSystem
    class FileSystemError(where: EelPath.Absolute, additionalMessage: String) : MoveException(where, additionalMessage), EelFsError.Other
    class Other(where: EelPath.Absolute, additionalMessage: String) : MoveException(where, additionalMessage), EelFsError.Other
  }

  /**
   * Time passed since Jan 1, 1970, 00:00.
   * [nanoseconds] represent the amount of time passed since the last _second_, i.e., they are never bigger than 999,999,999.
   */
  interface TimeSinceEpoch {
    val seconds: ULong
    val nanoseconds: UInt
  }

  interface ChangeAttributesOptions {
    fun accessTime(duration: TimeSinceEpoch): ChangeAttributesOptions
    val accessTime: TimeSinceEpoch?
    fun modificationTime(duration: TimeSinceEpoch): ChangeAttributesOptions
    val modificationTime: TimeSinceEpoch?
    fun permissions(permissions: EelFileInfo.Permissions): ChangeAttributesOptions
    val permissions: EelFileInfo.Permissions?
  }

  sealed class ChangeAttributesException(where: EelPath.Absolute, additionalMessage: String) : EelFsIOException(where, additionalMessage) {
    class SourceDoesNotExist(where: EelPath.Absolute) : ChangeAttributesException(where, "Source does not exist"), EelFsError.DoesNotExist
    class PermissionDenied(where: EelPath.Absolute) : ChangeAttributesException(where, "Permission denied"), EelFsError.PermissionDenied
    class NameTooLong(where: EelPath.Absolute) : ChangeAttributesException(where, "Name too long"), EelFsError.NameTooLong
    class Other(where: EelPath.Absolute, additionalMessage: String) : ChangeAttributesException(where, additionalMessage), EelFsError.Other
  }

  @Throws(ChangeAttributesException::class)
  suspend fun changeAttributes(path: EelPath.Absolute, options: ChangeAttributesOptions)

  suspend fun createTemporaryDirectory(options: CreateTemporaryDirectoryOptions): EelResult<
    EelPath.Absolute,
    CreateTemporaryDirectoryError>

  interface CreateTemporaryDirectoryOptions {
    fun prefix(prefix: String): CreateTemporaryDirectoryOptions
    val prefix: String

    fun suffix(suffix: String): CreateTemporaryDirectoryOptions
    val suffix: String

    fun deleteOnExit(deleteOnExit: Boolean): CreateTemporaryDirectoryOptions
    val deleteOnExit: Boolean

    fun parentDirectory(parentDirectory: EelPath.Absolute?): CreateTemporaryDirectoryOptions
    val parentDirectory: EelPath.Absolute?
  }

  sealed interface CreateTemporaryDirectoryError : EelFsError {
    interface NotDirectory : CreateTemporaryDirectoryError, EelFsError.NotDirectory
    interface PermissionDenied : CreateTemporaryDirectoryError, EelFsError.PermissionDenied
    interface Other : CreateTemporaryDirectoryError, EelFsError.Other
  }

  companion object Arguments {
    @JvmStatic
    fun writeOptionsBuilder(path: EelPath.Absolute): WriteOptions =
      WriteOptionsImpl(path)

    @JvmStatic
    fun copyOptionsBuilder(source: EelPath.Absolute, target: EelPath.Absolute): CopyOptions =
      CopyOptionsImpl(source, target)

    @JvmStatic
    fun changeAttributesBuilder(): ChangeAttributesOptions =
      ChangeAttributesOptionsImpl()

    @JvmStatic
    fun timeSinceEpoch(seconds: ULong, nanos: UInt): TimeSinceEpoch = TimeSinceEpochImpl(seconds, nanos)

    @JvmStatic
    fun createTemporaryDirectoryOptions(): CreateTemporaryDirectoryOptions =
      CreateTemporaryDirectoryOptionsImpl()
  }

  /**
   * Returns information about a logical disk that contains [path].
   */
  suspend fun getDiskInfo(path: EelPath.Absolute): EelResult<DiskInfo, DiskInfoError>

  interface DiskInfo {
    /**
     * Total capacity of a logical disk.
     * If more than [ULong.MAX_VALUE] available, then the returned value is [ULong.MAX_VALUE]
     */
    val totalSpace: ULong

    /**
     * The number of available bytes on a logical disk.
     * If more than [ULong.MAX_VALUE] available, then the returned value is [ULong.MAX_VALUE]
     */
    val availableSpace: ULong
  }

  sealed interface DiskInfoError : EelFsError {
    interface PathDoesNotExists : DiskInfoError, EelFsError.DoesNotExist
    interface PermissionDenied : DiskInfoError, EelFsError.PermissionDenied
    interface NameTooLong : DiskInfoError, EelFsError.NameTooLong
    interface Other : DiskInfoError, EelFsError.Other
  }
}

sealed interface EelOpenedFile {
  val path: EelPath.Absolute

  @Throws(CloseException::class)
  suspend fun close()

  sealed class CloseException(
    where: EelPath.Absolute,
    additionalMessage: String,
  ) : EelFsIOException(where, additionalMessage) {
    class Other(where: EelPath.Absolute, additionalMessage: String)
      : CloseException(where, additionalMessage), EelFsError.Other
  }

  suspend fun tell(): EelResult<
    Long,
    TellError>

  sealed interface TellError : EelFsError {
    interface Other : TellError, EelFsError.Other
  }

  suspend fun seek(offset: Long, whence: SeekWhence): EelResult<
    Long,
    SeekError>

  sealed interface SeekError : EelFsError {
    interface InvalidValue : SeekError, EelFsError
    interface UnknownFile : SeekError, EelFsError.UnknownFile
    interface Other : SeekError, EelFsError.Other
  }

  enum class SeekWhence {
    START, CURRENT, END,
  }

  /**
   * Similar to `fstat(2)`.
   *
   * Sometimes, the files are inaccessible via [EelFileSystemApi.stat] -- for example, if they are deleted.
   * In this case, one can get the information about the opened file with the use of this function.
   */
  suspend fun stat(): EelResult<EelFileInfo, StatError>


  interface Reader : EelOpenedFile {

    /**
     * Reads data from the current position of the file (see [tell])
     *
     * If the remote file is read completely, then this function returns [ReadResult] with [ReadResult.EOF].
     * Otherwise, if there are any data left to read, then it returns [ReadResult.Bytes].
     * Note, that [ReadResult.Bytes] can be `0` if [buf] cannot accept new data.
     *
     * This operation modifies the file's cursor, i.e. [tell] may show different results before and after this function is invoked.
     *
     * The implementation MAY read less data than the capacity of the buffer even if it's possible to read the whole requested buffer.
     */
    suspend fun read(buf: ByteBuffer): EelResult<ReadResult, ReadError>

    /**
     * Reads data from the position [offset] of the file.
     *
     * This operation does not modify the file's cursor, i.e. [tell] will show the same result before and after this function is invoked.
     *
     * The implementation MAY read less than [offset] bytes even if it's possible to read the whole requested buffer.
     */
    suspend fun read(buf: ByteBuffer, offset: Long): EelResult<ReadResult, ReadError>

    sealed interface ReadResult {
      interface EOF : ReadResult
      interface Bytes : ReadResult {
        val bytesRead: Int
      }
    }

    sealed interface ReadError : EelFsError {
      interface UnknownFile : ReadError, EelFsError.UnknownFile
      interface InvalidValue : ReadError, EelFsError
      interface Other : ReadError, EelFsError.Other
    }
  }

  interface Writer : EelOpenedFile {
    /**
     * TODO Document
     *
     * The implementation MAY write the part of the [buf] even if it's possible to write the whole buffer.
     */
    suspend fun write(buf: ByteBuffer): EelResult<
      Int,
      WriteError>

    /**
     * TODO Document
     *
     * The implementation MAY write the part of the [buf] even if it's possible to write the whole buffer.
     */
    suspend fun write(buf: ByteBuffer, pos: Long): EelResult<
      Int,
      WriteError>

    sealed interface WriteError : EelFsError {
      interface InvalidValue : WriteError, EelFsError
      sealed interface ResourceExhausted : WriteError, EelFsError.Other {
        interface DiskQuotaExceeded : ResourceExhausted, EelFsError.Other
        interface FileSizeExceeded : ResourceExhausted, EelFsError.Other
        interface NoSpaceLeft : ResourceExhausted, EelFsError.Other
      }

      interface UnknownFile : WriteError, EelFsError.UnknownFile
      interface Other : WriteError, EelFsError.Other
    }

    @Throws(FlushException::class)
    suspend fun flush()

    sealed class FlushException(
      where: EelPath.Absolute,
      additionalMessage: String,
    ) : EelFsIOException(where, additionalMessage) {
      class Other(where: EelPath.Absolute, additionalMessage: String)
        : FlushException(where, additionalMessage), EelFsError.Other
    }

    @Throws(TruncateException::class)
    suspend fun truncate(size: Long)

    sealed class TruncateException(
      where: EelPath.Absolute,
      additionalMessage: String,
    ) : EelFsIOException(where, additionalMessage) {
      class UnknownFile(where: EelPath.Absolute) : TruncateException(where, "Could not find opened file"), EelFsError.UnknownFile
      class NegativeOffset(where: EelPath.Absolute, offset: Long) : TruncateException(where, "Offset $offset is negative")
      class OffsetTooBig(where: EelPath.Absolute, offset: Long) : TruncateException(where, "Offset $offset is too big for truncation")
      class ReadOnlyFs(where: EelPath.Absolute) : TruncateException(where, "File system is read-only"), EelFsError.ReadOnlyFileSystem
      class Other(where: EelPath.Absolute, additionalMessage: String)
        : TruncateException(where, additionalMessage), EelFsError.Other
    }
  }

  interface ReaderWriter : Reader, Writer
}

interface EelFileSystemPosixApi : EelFileSystemApi {
  override val user: EelUserPosixInfo

  enum class CreateDirAttributePosix {
    // todo
  }

  @Throws(CreateDirectoryException::class)
  suspend fun createDirectory(path: EelPath.Absolute, attributes: List<CreateDirAttributePosix>)

  sealed class CreateDirectoryException(
    where: EelPath.Absolute,
    additionalMessage: String,
  ) : EelFsIOException(where, additionalMessage) {
    class DirAlreadyExists(where: EelPath.Absolute, additionalMessage: String) : CreateDirectoryException(where,
                                                                                                          additionalMessage), EelFsError.AlreadyExists

    class FileAlreadyExists(where: EelPath.Absolute, additionalMessage: String) : CreateDirectoryException(where,
                                                                                                           additionalMessage), EelFsError.AlreadyExists

    class ParentNotFound(where: EelPath.Absolute, additionalMessage: String) : CreateDirectoryException(where,
                                                                                                        additionalMessage), EelFsError.DoesNotExist

    class PermissionDenied(where: EelPath.Absolute, additionalMessage: String) : CreateDirectoryException(where,
                                                                                                          additionalMessage), EelFsError.PermissionDenied

    class Other(where: EelPath.Absolute, additionalMessage: String) : CreateDirectoryException(where, additionalMessage), EelFsError.Other
  }

  override suspend fun listDirectoryWithAttrs(
    path: EelPath.Absolute,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelPosixFileInfo>>,
    EelFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelPosixFileInfo,
    StatError>


  /**
   * Notice that the first argument is the target of the symlink,
   * like in `ln -s` tool, like in `symlink(2)` from LibC, but opposite to `java.nio.file.spi.FileSystemProvider.createSymbolicLink`.
   */
  @Throws(CreateSymbolicLinkException::class)
  suspend fun createSymbolicLink(target: EelPath, linkPath: EelPath.Absolute)

  sealed class CreateSymbolicLinkException(
    where: EelPath.Absolute,
    additionalMessage: String,
  ) : EelFsIOException(where, additionalMessage) {
    /**
     * Example: `createSymbolicLink("anywhere", "/directory_that_does_not_exist")`
     */
    class DoesNotExist(where: EelPath.Absolute, additionalMessage: String) : CreateSymbolicLinkException(where,
                                                                                                         additionalMessage), EelFsError.DoesNotExist

    /**
     * Examples:
     * * `createSymbolicLink("anywhere", "/etc/passwd")`
     * * `createSymbolicLink("anywhere", "/home")`
     */
    class FileAlreadyExists(where: EelPath.Absolute, additionalMessage: String) : CreateSymbolicLinkException(where,
                                                                                                              additionalMessage), EelFsError.AlreadyExists

    /**
     * Example: `createSymbolicLink("anywhere", "/etc/passwd/oops")`
     */
    class NotDirectory(where: EelPath.Absolute, additionalMessage: String) : CreateSymbolicLinkException(where,
                                                                                                         additionalMessage), EelFsError.NotDirectory

    /**
     * Example:
     * * With non-root permissions: `createSymbolicLink("anywhere", "/root/oops")`
     */
    class PermissionDenied(where: EelPath.Absolute, additionalMessage: String) : CreateSymbolicLinkException(where,
                                                                                                             additionalMessage), EelFsError.PermissionDenied

    /**
     * Everything else, including `ELOOP`.
     * Despite an allegedly related name, the errno `ELOOP` has nothing to do with symlinks creation,
     * and it can appear only in this case:
     * ```
     * createSymbolicLink("/tmp/foobar", "/tmp/foobar") // OK
     * createSymbolicLink("anywhere", "/tmp/foobar/oops") // Other("something about ELOOP")
     * ```
     */
    class Other(where: EelPath.Absolute, additionalMessage: String) : CreateSymbolicLinkException(where,
                                                                                                  additionalMessage), EelFsError.Other
  }
}

interface EelFileSystemWindowsApi : EelFileSystemApi {
  override val user: EelUserWindowsInfo

  suspend fun getRootDirectories(): Collection<EelPath.Absolute>

  override suspend fun listDirectoryWithAttrs(
    path: EelPath.Absolute,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelWindowsFileInfo>>,
    EelFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelWindowsFileInfo,
    StatError>
}

suspend fun EelFileSystemApi.changeAttributes(path: EelPath.Absolute, setup: EelFileSystemApi.ChangeAttributesOptions.() -> Unit) {
  val options = EelFileSystemApi.changeAttributesBuilder().apply(setup)
  return changeAttributes(path, options)
}

suspend fun EelFileSystemApi.openForWriting(path: EelPath.Absolute, setup: (EelFileSystemApi.WriteOptions).() -> Unit): EelResult<EelOpenedFile.Writer, EelFileSystemApi.FileWriterError> {
  val options = EelFileSystemApi.writeOptionsBuilder(path).apply(setup)
  return openForWriting(options)
}

suspend fun EelFileSystemApi.copy(source: EelPath.Absolute, target: EelPath.Absolute, setup: (EelFileSystemApi.CopyOptions).() -> Unit) {
  val options = EelFileSystemApi.copyOptionsBuilder(source, target).apply(setup)
  return copy(options)
}

suspend fun EelFileSystemApi.createTemporaryDirectory(setup: (EelFileSystemApi.CreateTemporaryDirectoryOptions).() -> Unit): EelResult<EelPath.Absolute, EelFileSystemApi.CreateTemporaryDirectoryError> {
  val options = EelFileSystemApi.createTemporaryDirectoryOptions().apply(setup)
  return createTemporaryDirectory(options)
}