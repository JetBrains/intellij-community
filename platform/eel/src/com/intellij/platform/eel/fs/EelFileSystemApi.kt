// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi.StatError
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.nio.ByteBuffer
import java.nio.file.Path

@get:ApiStatus.Internal
val EelFileSystemApi.pathSeparator: String
  get() = when (this) {
    is EelFileSystemPosixApi -> ":"
    is EelFileSystemWindowsApi -> ";"
    else -> throw UnsupportedOperationException("Unsupported OS: ${this::class.java}")
  }

@ApiStatus.Internal
fun EelDescriptor.getPath(string: String): EelPath {
  return EelPath.parse(string, this)
}

@ApiStatus.Internal
fun EelFileSystemApi.getPath(string: String): EelPath {
  return EelPath.parse(string, descriptor)
}

@ApiStatus.Internal
interface LocalEelFileSystemApi : EelFileSystemApi

// TODO Integrate case-(in)sensitiveness into the interface.

@ApiStatus.Internal
interface EelFileSystemApi {

  /**
   * There's a duplication of methods because [user] is required for checking file permissions correctly, but also it can be required
   * in other cases outside the filesystem.
   */
  val user: EelUserInfo

  val descriptor: EelDescriptor

  /**
   * Returns names of files in a directory. If [path] is a symlink, it will be resolved, but no symlinks are resolved among children.
   */
  @CheckReturnValue
  suspend fun listDirectory(path: EelPath): EelResult<
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
  @Deprecated("Use the method with the builder")
  suspend fun listDirectoryWithAttrs(
    path: EelPath,
    symlinkPolicy: SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelFileInfo>>,
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
  suspend fun listDirectoryWithAttrs(@GeneratedBuilder args: ListDirectoryWithAttrsArgs): EelResult<
    Collection<Pair<String, EelFileInfo>>,
    ListDirectoryError> =
    listDirectoryWithAttrs(path = args.path, symlinkPolicy = args.symlinkPolicy)

  interface ListDirectoryWithAttrsArgs {
    val path: EelPath
    val symlinkPolicy: SymlinkPolicy get() = SymlinkPolicy.DO_NOT_RESOLVE
  }

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
  suspend fun canonicalize(path: EelPath): EelResult<
    EelPath,
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
  @Deprecated("Use the method with the builder")
  suspend fun stat(path: EelPath, symlinkPolicy: SymlinkPolicy): EelResult<EelFileInfo, StatError>

  /**
   * Similar to stat(2) and lstat(2). [symlinkPolicy] has an impact only on [EelFileInfo.type] if [path] points on a symlink.
   */
  @CheckReturnValue
  suspend fun stat(@GeneratedBuilder args: StatArgs): EelResult<EelFileInfo, StatError> =
    stat(path = args.path, symlinkPolicy = args.symlinkPolicy)

  interface StatArgs {
    val path: EelPath
    val symlinkPolicy: SymlinkPolicy get() = SymlinkPolicy.DO_NOT_RESOLVE
  }

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
  suspend fun sameFile(source: EelPath, target: EelPath): EelResult<
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
  suspend fun openForReading(path: EelPath): EelResult<
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

  enum class OverflowPolicy {
    DROP,
    RETAIN,
  }

  sealed interface FullReadResult {
    interface Overflow : FullReadResult
    interface BytesOverflown : FullReadResult {
      val bytes: ByteArray
    }

    interface Bytes : FullReadResult {
      val bytes: ByteArray
    }
  }

  @CheckReturnValue
  suspend fun readFully(path: EelPath, limit: ULong, overflowPolicy: OverflowPolicy): EelResult<FullReadResult, FullReadError>

  sealed interface FullReadError : EelFsError {
    interface DoesNotExist : FullReadError, EelFsError.DoesNotExist
    interface PermissionDenied : FullReadError, EelFsError.PermissionDenied
    interface NotFile : FullReadError, EelFsError.NotFile
    interface Other : FullReadError, EelFsError.Other
  }

   /**
   * Calculates a xxHash3 hash for each file in the given directory in a BFS manner. The provided path can point to a nonexistent file or\
   * directory when the target EelPath is on the remote side (this would indicate that the file/directory has been created locally).\
   * EelPath pointing to a local file/directory has to be valid.
   *
   * @param path is the target directory through which will be recursed.
   * @return a flow which emits a tuple of file EelPath and its hash.
   */
  @CheckReturnValue
  suspend fun directoryHash(path: EelPath): Flow<DirectoryHashEntry>

  sealed class DirectoryHashEntry {
    data class Hash(val path: EelPath, val hash: Long) : DirectoryHashEntry()
    data class Error(val error: DirectoryHashError) : DirectoryHashEntry()
  }

  sealed interface DirectoryHashError : EelFsError {
    interface Other : DirectoryHashError, EelFsError.Other
  }

  /**
   * Opens the file only for writing
   */
  @CheckReturnValue
  suspend fun openForWriting(
    @GeneratedBuilder options: WriteOptions,
  ): EelResult<
    EelOpenedFile.Writer,
    FileWriterError>

  sealed interface WriteOptions {
    val path: EelPath
    val append: Boolean get() = false
    val truncateExisting: Boolean get() = true
    val creationMode: FileWriterCreationMode get() = FileWriterCreationMode.ALLOW_CREATE

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
      fun Builder(path: EelPath): Builder = WriteOptionsImpl2(path)
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
  suspend fun openForReadingAndWriting(@GeneratedBuilder options: WriteOptions): EelResult<EelOpenedFile.ReaderWriter, FileWriterError>

  @CheckReturnValue
  suspend fun delete(path: EelPath, removeContent: Boolean): EelResult<Unit, DeleteError>

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
  suspend fun copy(@GeneratedBuilder options: CopyOptions): EelResult<Unit, CopyError>

  sealed interface CopyOptions {
    val source: EelPath
    val target: EelPath
    val copyRecursively: Boolean get() = false
    val replaceExisting: Boolean get() = false
    val preserveAttributes: Boolean get() = false
    val interruptible: Boolean get() = false
    val followLinks: Boolean get() = false

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
      fun Builder(source: EelPath, target: EelPath): Builder = CopyOptionsImpl2(source, target)
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
  @Deprecated("Use the method with the builder")
  suspend fun move(
    source: EelPath,
    target: EelPath,
    replaceExisting: ReplaceExistingDuringMove,
    followLinks: Boolean,
  ): EelResult<Unit, MoveError>

  @CheckReturnValue
  suspend fun move(@GeneratedBuilder args: MoveArgs): EelResult<Unit, MoveError> =
    move(source = args.source, target = args.target, replaceExisting = args.replaceExisting, followLinks = args.followLinks)

  interface MoveArgs {
    val source: EelPath
    val target: EelPath
    val replaceExisting: ReplaceExistingDuringMove get() = ReplaceExistingDuringMove.REPLACE_EVERYTHING
    val followLinks: Boolean get() = false
  }

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
    val path: EelPath
    val accessTime: TimeSinceEpoch? get() = null
    val modificationTime: TimeSinceEpoch? get() = null
    val permissions: EelFileInfo.Permissions? get() = null

    interface Builder {
      fun permissions(permissions: EelFileInfo.Permissions): Builder
      fun modificationTime(duration: TimeSinceEpoch): Builder
      fun accessTime(duration: TimeSinceEpoch): Builder

      fun build(): ChangeAttributesOptions
    }

    companion object {
      fun Builder(): Builder = ChangeAttributesOptionsImpl2()
    }
  }

  sealed interface ChangeAttributesError : EelFsError {
    interface SourceDoesNotExist : ChangeAttributesError, EelFsError.DoesNotExist
    interface PermissionDenied : ChangeAttributesError, EelFsError.PermissionDenied
    interface NameTooLong : ChangeAttributesError, EelFsError.NameTooLong
    interface Other : ChangeAttributesError, EelFsError.Other
  }

  @CheckReturnValue
  @Deprecated("Use the method with the builder")
  suspend fun changeAttributes(path: EelPath, options: ChangeAttributesOptions): EelResult<Unit, ChangeAttributesError>

  @CheckReturnValue
  suspend fun changeAttributes(@GeneratedBuilder args: ChangeAttributesOptions): EelResult<Unit, ChangeAttributesError> =
    changeAttributes(path = args.path, options = args)

  @CheckReturnValue
  suspend fun createTemporaryDirectory(@GeneratedBuilder options: CreateTemporaryEntryOptions): EelResult<
    EelPath,
    CreateTemporaryEntryError>

  @CheckReturnValue
  suspend fun createTemporaryFile(@GeneratedBuilder options: CreateTemporaryEntryOptions): EelResult<EelPath, CreateTemporaryEntryError>

  interface CreateTemporaryEntryOptions {
    val prefix: String get() = ""
    val suffix: String get() = ""
    val deleteOnExit: Boolean get() = false
    val parentDirectory: EelPath? get() = null

    interface Builder {
      fun prefix(prefix: String): Builder
      fun suffix(suffix: String): Builder
      fun deleteOnExit(deleteOnExit: Boolean): Builder
      fun parentDirectory(parentDirectory: EelPath?): Builder
      fun build(): CreateTemporaryEntryOptions
    }

    companion object {
      fun Builder(): Builder = CreateTemporaryEntryOptionsImpl2()
    }
  }

  sealed interface CreateTemporaryEntryError : EelFsError {
    interface NotDirectory : CreateTemporaryEntryError, EelFsError.NotDirectory
    interface PermissionDenied : CreateTemporaryEntryError, EelFsError.PermissionDenied
    interface Other : CreateTemporaryEntryError, EelFsError.Other
  }

  companion object Arguments {
    @JvmStatic
    fun timeSinceEpoch(seconds: ULong, nanos: UInt): TimeSinceEpoch = TimeSinceEpochImpl(seconds, nanos)
  }

  /**
   * Returns information about a logical disk that contains [path].
   */
  @CheckReturnValue
  suspend fun getDiskInfo(path: EelPath): EelResult<DiskInfo, DiskInfoError>

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

  /**
   * Subscribes to a file watcher to receive file change events.
   *
   * @return A flow emitting [PathChange] instances that indicate the path and type of change.
   *         Each path is an absolute path on the target system (container), for example, `/home/myproject/myfile.txt`
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun watchChanges(): Flow<PathChange> {
    throw UnsupportedOperationException()
  }

  /**
   * Adds the watched paths from the specified set of file paths. A path is watched till [unwatch] method is explicitly called for it.
   *
   * Use [WatchOptionsBuilder] to construct the watch configuration. Example:
   * ```
   * val flow = eel.fs.addWatchRoots(
   *     WatchOptionsBuilder()
   *         .changeTypes(setOf(EelFileSystemApi.FileChangeType.CHANGED))
   *         .paths(setOf(eelPath))
   *         .build())
   * ```
   *
   * @param watchOptions The options to use for file watching. See [WatchOptions]
   * @return True if the operation was successful.
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun addWatchRoots(@GeneratedBuilder watchOptions: WatchOptions): Boolean {
    throw UnsupportedOperationException()
  }

  /**
   * Unregisters a previously watched path.
   *
   * @param unwatchOptions The options specifying the path to be unwatched. See [UnwatchOptions].
   * @return True if the operation was successful. False if the path hadn't been previously watched or unwatch failed.
   *
   * @throws UnsupportedOperationException if the method isn't implemented for the file system.
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun unwatch(@GeneratedBuilder unwatchOptions: UnwatchOptions): Boolean {
    throw UnsupportedOperationException()
  }

  /**
   * Represents a change detected in a specific path within the file system. It can be a change in the child directory if a recursive
   * watch is enabled.
   *
   * @property path The absolute path in the file system associated with the change.
   *                For example, "/home/user/documents/file.txt".
   * @property type The type of change that occurred. See [FileChangeType],
   */
  interface PathChange {
    val path: String
    val type: FileChangeType
  }

  /**
   * Provides configurations for specifying which file paths should be monitored and what types of file system changes should be watched.
   *
   * @property paths The set of file paths to monitor for changes with additional watch properties. See [WatchedPath]
   * @property changeTypes The types of file system changes to monitor. This is a set of [FileChangeType] values.
   */
  interface WatchOptions {
    val paths: Set<WatchedPath> get() = emptySet()
    val changeTypes: Set<FileChangeType> get() = emptySet()
  }


  /**
   * Represents a file system path being monitored for changes.
   *
   * @property path The file system path being watched.
   * @property recursive Whether the file system changes should be monitored recursively within the specified path.
   * @see [watchChanges]
   */
  class WatchedPath internal constructor(val path: EelPath, val recursive: Boolean) {
    companion object {
      /**
       * Creates a WatchedPath from EelPath with recursive monitoring disabled.
       *
       * @param path the EelPath instance to be converted
       * @return a new *non-recursive* WatchedPath instance created from the provided EelPath.
       */
      fun from(path: EelPath): WatchedPath = WatchedPath(path, false)
    }

    /**
     * @return a `WatchedPath` instance with the same `path` as the current object, but with recursive monitoring enabled.
     */
    fun recursive(): WatchedPath = WatchedPath(path, true)
  }

  /**
   * Represents the options required to unregister a previously watched path in the file system.
   *
   * @property path The file system path to unwatch. Must be specified as an instance of [EelPath].
   * @see [unwatch]
   */
  interface UnwatchOptions {
    val path: EelPath
  }

  /**
   * Represents the type of change that can occur to a file in the file system.
   *
   * - `CREATED`: A file has been created.
   * - `DELETED`: A file has been deleted.
   * - `CHANGED`: A file has been modified (either its content or attributes have changed).
   */
  enum class FileChangeType { CREATED, DELETED, CHANGED }
}


@ApiStatus.Internal
sealed interface EelOpenedFile {
  val path: EelPath

  @CheckReturnValue
  suspend fun close(): EelResult<Unit, CloseError>

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
     * If the remote file is read completely,
     * then this function returns [ReadResult] with [ReadResult.EOF].
     * Otherwise, if there are any data left to read, then it returns [ReadResult.NOT_EOF].
     * See [ReadResult] for usage receipts.
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
    suspend fun flush(): EelResult<Unit, FlushError>

    sealed interface FlushError : EelFsError {
      interface Other : FlushError, EelFsError.Other
    }

    @CheckReturnValue
    suspend fun truncate(size: Long): EelResult<Unit, TruncateError>

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

@ApiStatus.Internal
interface LocalEelFileSystemPosixApi : EelFileSystemPosixApi, LocalEelFileSystemApi

@ApiStatus.Internal
interface EelFileSystemPosixApi : EelFileSystemApi {
  override val user: EelUserPosixInfo

  enum class CreateDirAttributePosix {
    // todo
  }

  @CheckReturnValue
  suspend fun createDirectory(path: EelPath, attributes: List<CreateDirAttributePosix>): EelResult<Unit, CreateDirectoryError>

  sealed interface CreateDirectoryError : EelFsError {
    interface DirAlreadyExists : CreateDirectoryError, EelFsError.AlreadyExists
    interface FileAlreadyExists : CreateDirectoryError, EelFsError.AlreadyExists
    interface ParentNotFound : CreateDirectoryError, EelFsError.DoesNotExist
    interface PermissionDenied : CreateDirectoryError, EelFsError.PermissionDenied
    interface Other : CreateDirectoryError, EelFsError.Other
  }

  @Deprecated("Use the method with the builder")
  @CheckReturnValue
  override suspend fun listDirectoryWithAttrs(
    path: EelPath,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelPosixFileInfo>>,
    EelFileSystemApi.ListDirectoryError>

  @CheckReturnValue
  override suspend fun listDirectoryWithAttrs(@GeneratedBuilder args: EelFileSystemApi.ListDirectoryWithAttrsArgs): EelResult<
    Collection<Pair<String, EelPosixFileInfo>>,
    EelFileSystemApi.ListDirectoryError> =
    listDirectoryWithAttrs(args)

  @Deprecated("Use the method with the builder")
  @CheckReturnValue
  override suspend fun stat(path: EelPath, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelPosixFileInfo,
    StatError>

  @CheckReturnValue
  override suspend fun stat(@GeneratedBuilder args: EelFileSystemApi.StatArgs): EelResult<EelPosixFileInfo, StatError> =
    stat(path = args.path, symlinkPolicy = args.symlinkPolicy)

  /**
   * Notice that the first argument is the target of the symlink,
   * like in `ln -s` tool, like in `symlink(2)` from LibC, but opposite to `java.nio.file.spi.FileSystemProvider.createSymbolicLink`.
   *
   * Here we provide a way to create symlinks to either relative or absolute location.
   */
  @CheckReturnValue
  suspend fun createSymbolicLink(target: SymbolicLinkTarget, linkPath: EelPath): EelResult<Unit, CreateSymbolicLinkError>

  sealed interface SymbolicLinkTarget {
    companion object {
      @JvmStatic
      fun Absolute(path: EelPath): Absolute {
        return AbsoluteSymbolicLinkTarget(path)
      }

      @JvmStatic
      fun Relative(parts: List<String>): Relative {
        return RelativeSymbolicLinkTarget(parts)
      }
    }

    /**
     * The created link will be pointing to some fixed location on an environment.
     */
    interface Absolute : SymbolicLinkTarget {
      val path: EelPath
    }

    /**
     * The created link will be pointing to a location relative to the path of the **link**.
     * Such symbolic links may be safe to copy even between different machines.
     *
     * Example:
     *
     * Before:
     * ```sh
     * /tmp/d$ ls -l
     * drwxr-xr-x 2 knisht knisht 4096 Dec 24 18:45 d1
     * ```
     * After `createSymbolicLink(Relative("./d1/.."), EelPath.parse("/tmp/d/link"))`:
     * ```sh
     * /tmp/d$ ls -l
     * drwxr-xr-x 2 knisht knisht 4096 Dec 24 18:45 d1
     * lrwxrwxrwx 1 knisht knisht    3 Dec 24 18:43 link -> ./d1/..
     * /tmp/d$ ls -l link2
     * drwxr-xr-x 2 knisht knisht 4096 Dec 24 18:45 d1
     * lrwxrwxrwx 1 knisht knisht    3 Dec 24 18:43 link -> ./d1/..
     * ```
     */
    interface Relative : SymbolicLinkTarget {
      val reference: List<String>
    }
  }

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

@ApiStatus.Internal
interface LocalEelFileSystemWindowsApi : EelFileSystemWindowsApi, LocalEelFileSystemApi

@ApiStatus.Internal
interface EelFileSystemWindowsApi : EelFileSystemApi {
  override val user: EelUserWindowsInfo

  suspend fun getRootDirectories(): Collection<EelPath>

  @Deprecated("Use the method with the builder")
  @CheckReturnValue
  override suspend fun listDirectoryWithAttrs(
    path: EelPath,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<
    Collection<Pair<String, EelWindowsFileInfo>>,
    EelFileSystemApi.ListDirectoryError>

  @CheckReturnValue
  override suspend fun listDirectoryWithAttrs(@GeneratedBuilder args: EelFileSystemApi.ListDirectoryWithAttrsArgs): EelResult<
    Collection<Pair<String, EelWindowsFileInfo>>,
    EelFileSystemApi.ListDirectoryError> =
    listDirectoryWithAttrs(path = args.path, symlinkPolicy = args.symlinkPolicy)

  @Deprecated("Use the method with the builder")
  @CheckReturnValue
  override suspend fun stat(path: EelPath, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelWindowsFileInfo,
    StatError>

  @CheckReturnValue
  override suspend fun stat(@GeneratedBuilder args: EelFileSystemApi.StatArgs): EelResult<EelWindowsFileInfo, StatError> =
    stat(path = args.path, symlinkPolicy = args.symlinkPolicy)
}

@CheckReturnValue
@Deprecated("Use the method with the builder")
@ApiStatus.Internal
suspend fun EelFileSystemApi.changeAttributes(
  path: EelPath,
  setup: (EelFileSystemApi.ChangeAttributesOptions.Builder).() -> Unit,
): EelResult<Unit, EelFileSystemApi.ChangeAttributesError> {
  val options = EelFileSystemApi.ChangeAttributesOptions.Builder().apply(setup).build()
  return changeAttributes(path, options)
}

@CheckReturnValue
@Deprecated("Use the method with the builder")
@ApiStatus.Internal
suspend fun EelFileSystemApi.openForWriting(path: EelPath, setup: (EelFileSystemApi.WriteOptions.Builder).() -> Unit): EelResult<EelOpenedFile.Writer, EelFileSystemApi.FileWriterError> {
  val options = EelFileSystemApi.WriteOptions.Builder(path).apply(setup).build()
  return openForWriting(options)
}

@CheckReturnValue
@Deprecated("Use the method with the builder")
@ApiStatus.Internal
suspend fun EelFileSystemApi.copy(
  source: EelPath,
  target: EelPath,
  setup: (EelFileSystemApi.CopyOptions.Builder).() -> Unit,
): EelResult<Unit, EelFileSystemApi.CopyError> {
  val options = EelFileSystemApi.CopyOptions.Builder(source, target).apply(setup).build()
  return copy(options)
}

@CheckReturnValue
@Deprecated("Use the method with the builder")
@ApiStatus.Internal
suspend fun EelFileSystemApi.createTemporaryDirectory(setup: (EelFileSystemApi.CreateTemporaryEntryOptions.Builder).() -> Unit): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> {
  val options = EelFileSystemApi.CreateTemporaryEntryOptions.Builder().apply(setup).build()
  return createTemporaryDirectory(options)
}