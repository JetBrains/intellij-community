// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelUserInfo
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.EelUserWindowsInfo
import com.intellij.platform.eel.fs.EelFileSystemApi.StatError
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.CheckReturnValue
import java.nio.ByteBuffer

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
  @CheckReturnValue
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
  @CheckReturnValue
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
  @CheckReturnValue
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
  @CheckReturnValue
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
  @CheckReturnValue
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
  @CheckReturnValue
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
  @CheckReturnValue
  suspend fun openForWriting(
    options: WriteOptions,
  ): EelResult<
    EelOpenedFile.Writer,
    FileWriterError>

  sealed interface WriteOptions {
    val path: EelPath.Absolute
    val append: Boolean
    val truncateExisting: Boolean
    val creationMode: FileWriterCreationMode

    interface Builder {
      /**
       * Whether to append new data to the end of file.
       * Default: `false`
       */
      fun append(v: Boolean): Builder

      /**
       * Whether to remove contents from the existing file.
       * Default: `false`
       */
      fun truncateExisting(v: Boolean): Builder

      /**
       * Defines the behavior if the written file does not exist
       * Default: [FileWriterCreationMode.ONLY_OPEN_EXISTING]
       */
      fun creationMode(v: FileWriterCreationMode): Builder

      fun build(): WriteOptions
    }

    companion object {
      fun Builder(path: EelPath.Absolute): Builder = WriteOptionsImpl(path)
    }
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

  @CheckReturnValue
  suspend fun openForReadingAndWriting(options: WriteOptions): EelResult<EelOpenedFile.ReaderWriter, FileWriterError>

  @CheckReturnValue
  suspend fun delete(path: EelPath.Absolute, removeContent: Boolean): EelResult<Unit, DeleteError>

  sealed interface DeleteError : EelFsError {
    interface DoesNotExist : DeleteError, EelFsError.DoesNotExist
    interface DirNotEmpty : DeleteError, EelFsError.DirNotEmpty
    interface PermissionDenied : DeleteError, EelFsError.PermissionDenied

    /**
     * Thrown only when `followLinks` is specified for [delete]
     */
    interface UnresolvedLink : DeleteError
    interface Other : DeleteError, EelFsError.Other
  }

  @CheckReturnValue
  suspend fun copy(options: CopyOptions) : EelResult<Unit, CopyError>

  sealed interface CopyOptions {
    val source: EelPath.Absolute
    val target: EelPath.Absolute
    val copyRecursively: Boolean
    val replaceExisting: Boolean
    val preserveAttributes: Boolean
    val interruptible: Boolean
    val followLinks: Boolean

    interface Builder {
      /**
       * Relevant for copying directories.
       * [copyRecursively] indicates whether the directory should be copied recursively.
       * If `false`, then only the directory itself is copied, resulting in an empty directory located at the target path
       */
      fun copyRecursively(v: Boolean): Builder

      fun replaceExisting(v: Boolean): Builder

      fun preserveAttributes(v: Boolean): Builder

      fun interruptible(v: Boolean): Builder

      fun followLinks(v: Boolean): Builder

      fun build(): CopyOptions
    }

    companion object {
      fun Builder(source: EelPath.Absolute, target: EelPath.Absolute): Builder = CopyOptionsImpl(source, target)
    }
  }

  sealed interface CopyError : EelFsError {
    interface SourceDoesNotExist : CopyError, EelFsError.DoesNotExist
    interface TargetAlreadyExists : CopyError, EelFsError.AlreadyExists
    interface PermissionDenied : CopyError, EelFsError.PermissionDenied
    interface NotEnoughSpace : CopyError, EelFsError.NotEnoughSpace
    interface NameTooLong : CopyError, EelFsError.NameTooLong
    interface ReadOnlyFileSystem : CopyError, EelFsError.ReadOnlyFileSystem
    interface FileSystemError : CopyError, EelFsError.Other
    interface TargetDirNotEmpty : CopyError, EelFsError.DirNotEmpty
    interface Other : CopyError, EelFsError.Other
  }

  enum class ReplaceExistingDuringMove {
    REPLACE_EVERYTHING,

    /** For compatibility with Java NIO. */
    DO_NOT_REPLACE_DIRECTORIES,

    DO_NOT_REPLACE,
  }

  @CheckReturnValue
  suspend fun move(
    source: EelPath.Absolute,
    target: EelPath.Absolute,
    replaceExisting: ReplaceExistingDuringMove,
    followLinks: Boolean,
  ) : EelResult<Unit, MoveError>

  sealed interface MoveError : EelFsError {
    interface SourceDoesNotExist : MoveError, EelFsError.DoesNotExist
    interface TargetAlreadyExists : MoveError, EelFsError.AlreadyExists
    interface TargetIsDirectory : MoveError, EelFsError.AlreadyExists
    interface PermissionDenied : MoveError, EelFsError.PermissionDenied
    interface NameTooLong : MoveError, EelFsError.NameTooLong
    interface ReadOnlyFileSystem : MoveError, EelFsError.ReadOnlyFileSystem
    interface FileSystemError : MoveError, EelFsError.Other
    interface Other : MoveError, EelFsError.Other
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
    val accessTime: TimeSinceEpoch?
    val modificationTime: TimeSinceEpoch?
    val permissions: EelFileInfo.Permissions?

    interface Builder {
      fun permissions(permissions: EelFileInfo.Permissions): Builder
      fun modificationTime(duration: TimeSinceEpoch): Builder
      fun accessTime(duration: TimeSinceEpoch): Builder

      fun build(): ChangeAttributesOptions
    }

    companion object {
      fun Builder(): Builder = ChangeAttributesOptionsImpl()
    }
  }

  sealed interface ChangeAttributesError : EelFsError {
    interface SourceDoesNotExist : ChangeAttributesError, EelFsError.DoesNotExist
    interface PermissionDenied : ChangeAttributesError, EelFsError.PermissionDenied
    interface NameTooLong : ChangeAttributesError, EelFsError.NameTooLong
    interface Other : ChangeAttributesError, EelFsError.Other
  }

  @CheckReturnValue
  suspend fun changeAttributes(path: EelPath.Absolute, options: ChangeAttributesOptions) : EelResult<Unit, ChangeAttributesError>

  @CheckReturnValue
  suspend fun createTemporaryDirectory(options: CreateTemporaryDirectoryOptions): EelResult<
    EelPath.Absolute,
    CreateTemporaryDirectoryError>

  interface CreateTemporaryDirectoryOptions {
    val prefix: String
    val suffix: String
    val deleteOnExit: Boolean
    val parentDirectory: EelPath.Absolute?

    interface Builder {
      fun prefix(prefix: String): Builder
      fun suffix(suffix: String): Builder
      fun deleteOnExit(deleteOnExit: Boolean): Builder
      fun parentDirectory(parentDirectory: EelPath.Absolute?): Builder
      fun build(): CreateTemporaryDirectoryOptions
    }

    companion object {
      fun Builder(): Builder = CreateTemporaryDirectoryOptionsImpl()
    }
  }

  sealed interface CreateTemporaryDirectoryError : EelFsError {
    interface NotDirectory : CreateTemporaryDirectoryError, EelFsError.NotDirectory
    interface PermissionDenied : CreateTemporaryDirectoryError, EelFsError.PermissionDenied
    interface Other : CreateTemporaryDirectoryError, EelFsError.Other
  }

  companion object Arguments {
    @JvmStatic
    fun timeSinceEpoch(seconds: ULong, nanos: UInt): TimeSinceEpoch = TimeSinceEpochImpl(seconds, nanos)
  }

  /**
   * Returns information about a logical disk that contains [path].
   */
  @CheckReturnValue
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

  @CheckReturnValue
  suspend fun close() : EelResult<Unit, CloseError>

  sealed interface CloseError : EelFsError {
    interface Other : CloseError, EelFsError.Other
  }

  @CheckReturnValue
  suspend fun tell(): EelResult<
    Long,
    TellError>

  sealed interface TellError : EelFsError {
    interface Other : TellError, EelFsError.Other
  }

  @CheckReturnValue
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
  @CheckReturnValue
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
    @CheckReturnValue
    suspend fun read(buf: ByteBuffer): EelResult<ReadResult, ReadError>

    /**
     * Reads data from the position [offset] of the file.
     *
     * This operation does not modify the file's cursor, i.e. [tell] will show the same result before and after this function is invoked.
     *
     * The implementation MAY read less than [offset] bytes even if it's possible to read the whole requested buffer.
     */
    @CheckReturnValue
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
    @CheckReturnValue
    suspend fun write(buf: ByteBuffer): EelResult<
      Int,
      WriteError>

    /**
     * TODO Document
     *
     * The implementation MAY write the part of the [buf] even if it's possible to write the whole buffer.
     */
    @CheckReturnValue
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

    @CheckReturnValue
    suspend fun flush() : EelResult<Unit, FlushError>

    sealed interface FlushError : EelFsError {
      interface Other : FlushError, EelFsError.Other
    }

    @CheckReturnValue
    suspend fun truncate(size: Long) : EelResult<Unit, TruncateError>

    sealed interface TruncateError : EelFsError {
      interface UnknownFile : TruncateError, EelFsError.UnknownFile
      interface NegativeOffset : TruncateError
      interface OffsetTooBig : TruncateError
      interface ReadOnlyFs : TruncateError, EelFsError.ReadOnlyFileSystem
      interface Other : TruncateError, EelFsError.Other
    }
  }

  interface ReaderWriter : Reader, Writer
}

interface EelFileSystemPosixApi : EelFileSystemApi {
  override val user: EelUserPosixInfo

  enum class CreateDirAttributePosix {
    // todo
  }

  @CheckReturnValue
  suspend fun createDirectory(path: EelPath.Absolute, attributes: List<CreateDirAttributePosix>) : EelResult<Unit, CreateDirectoryError>

  sealed interface CreateDirectoryError : EelFsError {
    interface DirAlreadyExists : CreateDirectoryError, EelFsError.AlreadyExists
    interface FileAlreadyExists : CreateDirectoryError, EelFsError.AlreadyExists
    interface ParentNotFound : CreateDirectoryError, EelFsError.DoesNotExist
    interface PermissionDenied : CreateDirectoryError, EelFsError.PermissionDenied
    interface Other : CreateDirectoryError, EelFsError.Other
  }

  @CheckReturnValue
  override suspend fun listDirectoryWithAttrs(
    path: EelPath.Absolute,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelPosixFileInfo>>,
    EelFileSystemApi.ListDirectoryError>

  @CheckReturnValue
  override suspend fun stat(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelPosixFileInfo,
    StatError>


  /**
   * Notice that the first argument is the target of the symlink,
   * like in `ln -s` tool, like in `symlink(2)` from LibC, but opposite to `java.nio.file.spi.FileSystemProvider.createSymbolicLink`.
   */
  @CheckReturnValue
  suspend fun createSymbolicLink(target: EelPath, linkPath: EelPath.Absolute) : EelResult<Unit, CreateSymbolicLinkError>

  sealed interface CreateSymbolicLinkError : EelFsError {
    /**
     * Example: `createSymbolicLink("anywhere", "/directory_that_does_not_exist")`
     */
    interface DoesNotExist : CreateSymbolicLinkError, EelFsError.DoesNotExist

    /**
     * Examples:
     * * `createSymbolicLink("anywhere", "/etc/passwd")`
     * * `createSymbolicLink("anywhere", "/home")`
     */
    interface FileAlreadyExists : CreateSymbolicLinkError, EelFsError.AlreadyExists

    /**
     * Example: `createSymbolicLink("anywhere", "/etc/passwd/oops")`
     */
    interface NotDirectory : CreateSymbolicLinkError, EelFsError.NotDirectory

    /**
     * Example:
     * * With non-root permissions: `createSymbolicLink("anywhere", "/root/oops")`
     */
    interface PermissionDenied : CreateSymbolicLinkError, EelFsError.PermissionDenied

    /**
     * Everything else, including `ELOOP`.
     * Despite an allegedly related name, the errno `ELOOP` has nothing to do with symlinks creation,
     * and it can appear only in this case:
     * ```
     * createSymbolicLink("/tmp/foobar", "/tmp/foobar") // OK
     * createSymbolicLink("anywhere", "/tmp/foobar/oops") // Other("something about ELOOP")
     * ```
     */
    interface Other : CreateSymbolicLinkError, EelFsError.Other
  }
}

interface EelFileSystemWindowsApi : EelFileSystemApi {
  override val user: EelUserWindowsInfo

  suspend fun getRootDirectories(): Collection<EelPath.Absolute>

  @CheckReturnValue
  override suspend fun listDirectoryWithAttrs(
    path: EelPath.Absolute,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelWindowsFileInfo>>,
    EelFileSystemApi.ListDirectoryError>

  @CheckReturnValue
  override suspend fun stat(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelWindowsFileInfo,
    StatError>
}

@CheckReturnValue
suspend fun EelFileSystemApi.changeAttributes(
  path: EelPath.Absolute,
  setup: (EelFileSystemApi.ChangeAttributesOptions.Builder).() -> Unit,
): EelResult<Unit, EelFileSystemApi.ChangeAttributesError> {
  val options = EelFileSystemApi.ChangeAttributesOptions.Builder().apply(setup).build()
  return changeAttributes(path, options)
}

@CheckReturnValue
suspend fun EelFileSystemApi.openForWriting(path: EelPath.Absolute, setup: (EelFileSystemApi.WriteOptions.Builder).() -> Unit): EelResult<EelOpenedFile.Writer, EelFileSystemApi.FileWriterError> {
  val options = EelFileSystemApi.WriteOptions.Builder(path).apply(setup).build()
  return openForWriting(options)
}

@CheckReturnValue
suspend fun EelFileSystemApi.copy(
  source: EelPath.Absolute,
  target: EelPath.Absolute,
  setup: (EelFileSystemApi.CopyOptions.Builder).() -> Unit,
): EelResult<Unit, EelFileSystemApi.CopyError> {
  val options = EelFileSystemApi.CopyOptions.Builder(source, target).apply(setup).build()
  return copy(options)
}

@CheckReturnValue
suspend fun EelFileSystemApi.createTemporaryDirectory(setup: (EelFileSystemApi.CreateTemporaryDirectoryOptions.Builder).() -> Unit): EelResult<EelPath.Absolute, EelFileSystemApi.CreateTemporaryDirectoryError> {
  val options = EelFileSystemApi.CreateTemporaryDirectoryOptions.Builder().apply(setup).build()
  return createTemporaryDirectory(options)
}