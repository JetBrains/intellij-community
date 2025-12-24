// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.EelUserWindowsInfo
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.fs.EelFileSystemApi.FileWriterCreationMode.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.util.io.ByteBufferUtil
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.*
import kotlin.streams.asSequence

abstract class NioBasedEelFileSystemApi(@VisibleForTesting val fs: FileSystem) : EelFileSystemApi {
  protected fun EelPath.toNioPath(): Path =
    fs.getPath(toString())

  override suspend fun getDiskInfo(path: EelPath): EelResult<EelFileSystemApi.DiskInfo, EelFileSystemApi.DiskInfoError> {
    val store = path.toNioPath().fileStore()
    return EelFsResultImpl.Ok(EelFsResultImpl.DiskInfoImpl(
      totalSpace = store.totalSpace.toULong(),
      availableSpace = store.usableSpace.toULong(),
    ))
  }

  inline fun <T, reified E : EelFsError> wrapIntoEelResult(body: () -> T): EelResult<T, E> =
    wrapIntoEelResult(null, body)

  inline fun <T, reified E : EelFsError> wrapIntoEelResult(isClosed: AtomicReference<Boolean?>?, body: () -> T): EelResult<T, E> =
    try {
      val result = EelFsResultImpl.Ok(body())
      isClosed?.updateAndGet { it ?: false }
      result
    }
    catch (err: FileSystemException) {
      isClosed?.set(true)
      val path =
        try {
          EelPath.parse(err.file.toString(), LocalEelDescriptor)
        }
        catch (_: EelPathException) {
          EelPath.parse(fs.rootDirectories.first().toString(), LocalEelDescriptor)
        }
      val err: EelFsError = when (err) {
        is AccessDeniedException -> EelFsResultImpl.PermissionDenied(path, err.message!!)
        is NoSuchFileException -> EelFsResultImpl.DoesNotExist(path, err.message!!)
        is FileAlreadyExistsException -> EelFsResultImpl.AlreadyExists(path, err.message!!)
        is NotDirectoryException -> EelFsResultImpl.NotDirectory(path, err.message!!)

        is NotLinkException -> EelFsResultImpl.Other(path, err.message!!)
        is FileSystemLoopException -> EelFsResultImpl.Other(path, err.message!!)
        is DirectoryNotEmptyException -> EelFsResultImpl.Other(path, err.message!!)
        is AtomicMoveNotSupportedException -> EelFsResultImpl.Other(path, err.message!!)
        else -> EelFsResultImpl.Other(path, err.message!!)
      }
      EelFsResultImpl.Error(err as? E ?: EelFsResultImpl.Other(path, err.message) as E)
    }

  override suspend fun listDirectory(path: EelPath): EelResult<Collection<String>, EelFileSystemApi.ListDirectoryError> =
    wrapIntoEelResult {
      Files.list(path.toNioPath()).use { stream ->
        stream.asSequence().mapTo(mutableListOf()) { it.fileName.toString() }
      }
    }

  override suspend fun listDirectoryWithAttrs(path: EelPath, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    out Collection<Pair<String, EelFileInfo>>,
    EelFileSystemApi.ListDirectoryError> =
    wrapIntoEelResult {
      Files.list(path.toNioPath()).use { stream ->
        stream.asSequence().mapNotNullTo(mutableListOf()) { child ->
          try {
            val fileAttributes = getFileAttributes(child, symlinkPolicy)
            child.fileName.toString() to fileAttributes
          }
          catch (_: FileSystemException) {
            // Happens on macOS with .VolumeIcon.icns
            null
          }
        }
      }
    }

  override suspend fun canonicalize(path: EelPath): EelResult<EelPath, EelFileSystemApi.CanonicalizeError> =
    wrapIntoEelResult {
      val realPath = path.toNioPath().toRealPath()
      EelPath.parse(realPath.toString(), LocalEelDescriptor)
    }

  override suspend fun stat(
    path: EelPath,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<out EelFileInfo, EelFileSystemApi.StatError> =
    wrapIntoEelResult {
      // TODO symlinkPolicy
      getFileAttributes(path.toNioPath(), symlinkPolicy)
    }

  override suspend fun sameFile(source: EelPath, target: EelPath): EelResult<Boolean, EelFileSystemApi.SameFileError> =
    wrapIntoEelResult {
      Files.isSameFile(source.toNioPath(), target.toNioPath())
    }

  @OptIn(EelDelicateApi::class)
  override suspend fun openForReading(args: EelFileSystemApi.OpenForReadingArgs): EelResult<
    EelOpenedFile.Reader,
    EelFileSystemApi.FileReaderError
    > {
    val result = openForReadingImpl(
      path = args.path,
      autoCloseAfterLastChunk = args.autoCloseAfterLastChunk,
      closeImmediatelyIfFileBiggerThan = args.closeImmediatelyIfFileBiggerThan,
    )
    // Not filling the buffer. This optimization makes no sense for the local case.
    args.readFirstChunkInto?.run {
      position(0)
      limit(0)
    }
    return result
  }

  @OptIn(EelDelicateApi::class)
  private fun openForReadingImpl(
    path: EelPath,
    autoCloseAfterLastChunk: Boolean,
    closeImmediatelyIfFileBiggerThan: Long?,
  ): EelResult<LocalEelOpenedFileReader, EelFileSystemApi.FileReaderError> = wrapIntoEelResult {
    val nioPath = path.toNioPath()
    val byteChannel: SeekableByteChannel = nioPath.fileSystem.provider().newByteChannel(nioPath, setOf(StandardOpenOption.READ))
    val isClosed = AtomicReference<Boolean?>(null)

    if (closeImmediatelyIfFileBiggerThan != null) {
      if (byteChannel.size() > closeImmediatelyIfFileBiggerThan) {
        isClosed.set(true)
        try {
          doClose(this@NioBasedEelFileSystemApi, byteChannel, path)
        }
        catch (_: IOException) {
          // Ignored.
        }
        return EelFsResultImpl.Error(EelFsResultImpl.FileBiggerThanRequested(path, "The file is bigger than the requested size"))
      }
    }

    LocalEelOpenedFileReader(
      eelFs = this,
      byteChannel = byteChannel,
      path_ = path,
      isClosed_ = isClosed,
      autoCloseAfterLastChunk = autoCloseAfterLastChunk,
    )
  }

  override suspend fun readFile(args: EelFileSystemApi.ReadFileArgs): EelResult<EelFileSystemApi.ReadFileResult, EelFileSystemApi.FileReaderError> =
    readFileImpl(args)

  override suspend fun openForWriting(options: EelFileSystemApi.WriteOptions): EelResult<
    EelOpenedFile.Writer,
    EelFileSystemApi.FileWriterError> =
    wrapIntoEelResult {
      val path = options.path
      val nioPath = path.toNioPath()
      val nioOptions = writeOptionsToNioOptions(options)
      val byteChannel: SeekableByteChannel = nioPath.fileSystem.provider().newByteChannel(nioPath, nioOptions)
      LocalEelOpenedFileWriter(this, byteChannel, path, AtomicReference(null))
    }

  override suspend fun openForReadingAndWriting(options: EelFileSystemApi.WriteOptions): EelResult<
    EelOpenedFile.ReaderWriter,
    EelFileSystemApi.FileWriterError> =
    wrapIntoEelResult {
      val path = options.path
      val nioPath = path.toNioPath()
      val nioOptions = writeOptionsToNioOptions(options)
      nioOptions += StandardOpenOption.READ
      val byteChannel: SeekableByteChannel = nioPath.fileSystem.provider().newByteChannel(nioPath, nioOptions)
      val isClosed = AtomicReference<Boolean?>(null)
      object : EelOpenedFile.ReaderWriter, EelOpenedFile.Writer by LocalEelOpenedFileWriter(this, byteChannel, path, isClosed) {
        override suspend fun read(buf: ByteBuffer): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
          doRead(this@NioBasedEelFileSystemApi, byteChannel, buf, isClosed, autoCloseAfterLastChunk = false)

        override suspend fun read(
          buf: ByteBuffer,
          offset: Long,
        ): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
          doRead(this@NioBasedEelFileSystemApi, byteChannel, offset, buf, isClosed)
      }
    }

  override suspend fun delete(path: EelPath, removeContent: Boolean): EelResult<Unit, EelFileSystemApi.DeleteError> =
    wrapIntoEelResult {
      if (removeContent) {
        Files.walkFileTree(path.asNioPath(), object : SimpleFileVisitor<Path>() {
          override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
          }

          override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) {
              throw exc
            }
            Files.delete(dir)
            return FileVisitResult.CONTINUE
          }
        })
      }
      else {
        Files.delete(path.toNioPath())
      }
    }

  override suspend fun copy(options: EelFileSystemApi.CopyOptions): EelResult<Unit, EelFileSystemApi.CopyError> =
    wrapIntoEelResult {
      Files.copy(
        options.source.toNioPath(),
        options.target.toNioPath(),
        *listOfNotNull(
          if (options.replaceExisting) StandardCopyOption.REPLACE_EXISTING else null,
          if (options.preserveAttributes) StandardCopyOption.COPY_ATTRIBUTES else null,
        ).toTypedArray(),
      )
    }

  override suspend fun move(
    source: EelPath,
    target: EelPath,
    replaceExisting: EelFileSystemApi.ReplaceExistingDuringMove,
    followLinks: Boolean,
  ): EelResult<Unit, EelFileSystemApi.MoveError> =
    wrapIntoEelResult {
      val sourceNioPath = source.toNioPath()
      val targetNioPath = target.toNioPath()
      if (targetNioPath.exists() && replaceExisting == EelFileSystemApi.ReplaceExistingDuringMove.DO_NOT_REPLACE) {
        return EelFsResultImpl.Error(EelFsResultImpl.TargetAlreadyExists(target, "Target already exists"))
      }
      withContext(Dispatchers.IO) {
        EelPathUtils.walkingTransfer(sourceNioPath, targetNioPath, removeSource = true, copyAttributes = true)
      }
    }
}

@EelDelicateApi
internal class LocalEelOpenedFileReader(
  private val eelFs: NioBasedEelFileSystemApi,
  private val byteChannel: SeekableByteChannel,
  private val path_: EelPath,
  private val isClosed_: AtomicReference<Boolean?>,
  private val autoCloseAfterLastChunk: Boolean,
) : EelOpenedFile.Reader {
  override val isClosed: Boolean?
    get() = isClosed_.get()

  override suspend fun read(buf: ByteBuffer): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
    doRead(eelFs, byteChannel, buf, isClosed_, autoCloseAfterLastChunk)

  override suspend fun read(buf: ByteBuffer, offset: Long): EelResult<
    ReadResult,
    EelOpenedFile.Reader.ReadError
    > =
    doRead(eelFs, byteChannel, offset, buf, isClosed_)

  override val path: EelPath = path_

  override suspend fun close(): EelResult<Unit, EelOpenedFile.CloseError> {
    isClosed_.set(true)
    return doClose(eelFs, byteChannel, path_)
  }

  override suspend fun tell(): EelResult<Long, EelOpenedFile.TellError> =
    doTell(eelFs, byteChannel, isClosed_)

  override suspend fun seek(offset: Long, whence: EelOpenedFile.SeekWhence): EelResult<Long, EelOpenedFile.SeekError> =
    doSeek(eelFs, byteChannel, path_, whence, offset, isClosed_)

  override suspend fun stat(): EelResult<EelFileInfo, EelFileSystemApi.StatError> =
    eelFs.stat(path_, EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW)
}

private class LocalEelOpenedFileWriter(
  private val eelFs: NioBasedEelFileSystemApi,
  private val byteChannel: SeekableByteChannel,
  private val path_: EelPath,
  private val isClosed_: AtomicReference<Boolean?>,
) : EelOpenedFile.Writer {
  @EelDelicateApi
  override val isClosed: Boolean?
    get() = isClosed_.get()

  override suspend fun write(buf: ByteBuffer): EelResult<Int, EelOpenedFile.Writer.WriteError> =
    eelFs.wrapIntoEelResult(isClosed_) {
      byteChannel.write(buf)
    }

  override suspend fun write(buf: ByteBuffer, pos: Long): EelResult<Int, EelOpenedFile.Writer.WriteError> =
    eelFs.wrapIntoEelResult(isClosed_) {
      val oldPosition = byteChannel.position()
      byteChannel.position(pos)
      val written = byteChannel.write(buf)
      byteChannel.position(oldPosition)
      written
    }

  override suspend fun flush(): EelResult<Unit, EelOpenedFile.Writer.FlushError> {
    return EelFsResultImpl.Ok(Unit)
  }

  override suspend fun truncate(size: Long): EelResult<Unit, EelOpenedFile.Writer.TruncateError> =
    eelFs.wrapIntoEelResult(isClosed_) {
      byteChannel.truncate(size)
    }

  override val path: EelPath = path_

  override suspend fun close(): EelResult<Unit, EelOpenedFile.CloseError> {
    isClosed_.set(true)
    return doClose(eelFs, byteChannel, path_)
  }

  override suspend fun tell(): EelResult<Long, EelOpenedFile.TellError> =
    doTell(eelFs, byteChannel, isClosed_)

  override suspend fun seek(offset: Long, whence: EelOpenedFile.SeekWhence): EelResult<Long, EelOpenedFile.SeekError> =
    doSeek(eelFs, byteChannel, path_, whence, offset, isClosed_)

  override suspend fun stat(): EelResult<EelFileInfo, EelFileSystemApi.StatError> =
    eelFs.stat(path_, EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW)
}

private fun doRead(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  buf: ByteBuffer,
  isClosed: AtomicReference<Boolean?>,
  autoCloseAfterLastChunk: Boolean,
): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
  eelFs.wrapIntoEelResult(isClosed) {
    val read = byteChannel.read(buf)
    val result = ReadResult.fromNumberOfReadBytes(read)
    if (autoCloseAfterLastChunk) {
      when (result) {
        ReadResult.EOF -> isClosed.set(true)
        ReadResult.NOT_EOF -> isClosed.set(false)
      }
    }
    result
  }

private fun doRead(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  offset: Long,
  buf: ByteBuffer,
  isClosed: AtomicReference<Boolean?>,
): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
  eelFs.wrapIntoEelResult(isClosed) {
    val oldPosition = byteChannel.position()
    byteChannel.position(offset)
    val read = byteChannel.read(buf)
    byteChannel.position(oldPosition)

    ReadResult.fromNumberOfReadBytes(read)
  }

private fun doSeek(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  path: EelPath,
  whence: EelOpenedFile.SeekWhence,
  offset: Long,
  isClosed: AtomicReference<Boolean?>,
): EelResult<Long, EelOpenedFile.SeekError> = eelFs.wrapIntoEelResult(isClosed) {
  val newPosition = when (whence) {
    EelOpenedFile.SeekWhence.START -> offset
    EelOpenedFile.SeekWhence.CURRENT -> byteChannel.position() + offset
    EelOpenedFile.SeekWhence.END -> byteChannel.size() - offset
  }
  byteChannel.position(newPosition)
  newPosition
}

private fun doTell(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  isClosed: AtomicReference<Boolean?>,
): EelResult<Long, EelOpenedFile.TellError> =
  eelFs.wrapIntoEelResult(isClosed) {
    byteChannel.position().toLong()
  }

private fun doClose(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  path: EelPath,
): EelResult<Unit, EelOpenedFile.CloseError> =
  eelFs.wrapIntoEelResult {
    byteChannel.close()
  }

abstract class PosixNioBasedEelFileSystemApi(
  fs: FileSystem,
  override val user: EelUserPosixInfo,
) : NioBasedEelFileSystemApi(fs), LocalEelFileSystemPosixApi {

  override suspend fun createDirectory(
    path: EelPath,
    attributes: List<EelFileSystemPosixApi.CreateDirAttributePosix>,
  ): EelResult<Unit, EelFileSystemPosixApi.CreateDirectoryError> =
    wrapIntoEelResult {
      Files.createDirectory(path.toNioPath())
    }

  override suspend fun listDirectoryWithAttrs(path: EelPath, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    Collection<Pair<String, EelPosixFileInfo>>,
    EelFileSystemApi.ListDirectoryError> =
    super<NioBasedEelFileSystemApi>.listDirectoryWithAttrs(path, symlinkPolicy) as EelResult<Collection<Pair<String, EelPosixFileInfo>>, EelFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: EelPath, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelPosixFileInfo,
    EelFileSystemApi.StatError> =
    super<NioBasedEelFileSystemApi>.stat(path, symlinkPolicy) as EelResult<EelPosixFileInfo, EelFileSystemApi.StatError>

  override suspend fun changeAttributes(
    path: EelPath,
    options: EelFileSystemApi.ChangeAttributesOptions,
  ): EelResult<Unit, EelFileSystemApi.ChangeAttributesError> =
    wrapIntoEelResult {
      val permissions = options.permissions as EelPosixFileInfo.Permissions?

      val view = Files.getFileAttributeView(path.toNioPath(), PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
      check(view != null) { path.toString() }
      val oldAttrs = view.readAttributes()
      copyTimes(oldAttrs, options, view)

      if (permissions != null) {
        view.setPermissions(setOfNotNull(
          if (permissions.ownerCanRead) PosixFilePermission.OWNER_READ else null,
          if (permissions.ownerCanWrite) PosixFilePermission.OWNER_WRITE else null,
          if (permissions.ownerCanExecute) PosixFilePermission.OWNER_EXECUTE else null,
          if (permissions.groupCanRead) PosixFilePermission.GROUP_READ else null,
          if (permissions.groupCanWrite) PosixFilePermission.GROUP_WRITE else null,
          if (permissions.groupCanExecute) PosixFilePermission.GROUP_EXECUTE else null,
          if (permissions.otherCanRead) PosixFilePermission.OTHERS_READ else null,
          if (permissions.otherCanWrite) PosixFilePermission.OTHERS_WRITE else null,
          if (permissions.otherCanExecute) PosixFilePermission.OTHERS_EXECUTE else null,
        ))
      }
    }

  override suspend fun createSymbolicLink(
    target: EelFileSystemPosixApi.SymbolicLinkTarget,
    linkPath: EelPath,
  ): EelResult<Unit, EelFileSystemPosixApi.CreateSymbolicLinkError> =
    wrapIntoEelResult {
      val targetPath = when (target) {
        is EelFileSystemPosixApi.SymbolicLinkTarget.Absolute -> target.path.toNioPath()
        is EelFileSystemPosixApi.SymbolicLinkTarget.Relative -> Path.of(target.reference.first(), *target.reference.drop(1).toTypedArray())
      }

      Files.createSymbolicLink(linkPath.toNioPath(), targetPath)
    }

  override suspend fun streamingWrite(chunks: Flow<ByteBuffer>, targetFileOpenOptions: EelFileSystemApi.WriteOptions): StreamingWriteResult = doStreamingWrite(chunks, targetFileOpenOptions)

  override suspend fun streamingRead(path: EelPath): Flow<StreamingReadResult> = doStreamingRead(path)

  override suspend fun walkDirectory(options: EelFileSystemApi.WalkDirectoryOptions): Flow<WalkDirectoryEntryResult> = flow {
    val rootDir = options.path.asNioPath()

    // the target path has to be a directory and needs to exist
    if (!rootDir.exists(LinkOption.NOFOLLOW_LINKS)) {
      val e = WalkDirectoryEntryResultImpl.Error(EelFsResultImpl.DoesNotExist(options.path, "provided path does not exist"))
      emit(e)
      return@flow
    }

    val emptyFileHash = Hashing.xxh3_64().hashStream().asLong
    val maxDepth = options.maxDepth

    when (options.traversalOrder) {
      EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryTraversalOrder.BFS -> {
        val q = ArrayDeque<Path>()
        q.addLast(rootDir)

        var currentDepth = 0
        while (q.isNotEmpty()) {
          val n = q.size
          repeat(n) {
            val currentItem = q.removeFirst()

            val sourceAttrs = currentItem.fileAttributesView<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
            walkDirectoryProcessFilePosix(currentItem, sourceAttrs, emptyFileHash, options)?.let { res -> emit(res) }

            // maxDepth < 0 means that there is not limit on the depth
            if (sourceAttrs.isDirectory && (maxDepth < 0 || currentDepth < maxDepth)) {
              var children = currentItem.listDirectoryEntries()
              children = when (options.entryOrder) {
                EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.RANDOM -> {
                  children
                }
                EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.ALPHABETICAL -> {
                  children.sortedBy { it.pathString }
                }
              }
              q.addAll(children)
            }
          }
          currentDepth += 1
        }
      }

      EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryTraversalOrder.DFS -> {
        when (options.entryOrder) {
          EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.RANDOM -> {
            // maxDepth == 0 means that there is not limit on the depth
            val maxDepth = if (maxDepth < 0) Integer.MAX_VALUE else maxDepth
            Files.walk(rootDir, maxDepth).use { pathStream ->
              for (path in pathStream) {
                val sourceAttrs = path.fileAttributesView<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
                walkDirectoryProcessFilePosix(path, sourceAttrs, emptyFileHash, options)?.let { res -> emit(res) }
              }
            }
          }
          EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.ALPHABETICAL -> {
            val q = mutableListOf<Pair<Path, Int>>()
            q.addLast(Pair(rootDir, 0))

            while (q.isNotEmpty()) {
              val (currentItem, currDepth) = q.removeLast()

              val sourceAttrs = currentItem.fileAttributesView<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
              walkDirectoryProcessFilePosix(currentItem, sourceAttrs, emptyFileHash, options)?.let { res -> emit(res) }

              // maxDepth < 0 means that there is not limit on the depth
              if (sourceAttrs.isDirectory && (maxDepth < 0 || currDepth < maxDepth)) {
                val children = currentItem
                  .listDirectoryEntries()
                  .sortedByDescending { it.pathString }
                  .map { path -> Pair(path, currDepth + 1) }
                q.addAll(children)
              }
            }
          }
        }
      }
    }
  }

  private fun walkDirectoryProcessFilePosix(
    currentItem: Path,
    sourceAttrs: PosixFileAttributes,
    emptyFileHash: Long,
    options: EelFileSystemApi.WalkDirectoryOptions,
  ): WalkDirectoryEntryResult? {
    var creationTime: ZonedDateTime? = null
    var lastModifiedTime: ZonedDateTime? = null
    var lastAccessTime: ZonedDateTime? = null
    if (options.readMetadata) {
      creationTime = sourceAttrs.creationTime()?.let { ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of("UTC")) }
      lastModifiedTime = sourceAttrs.lastModifiedTime()?.let { ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of("UTC")) }
      lastAccessTime = sourceAttrs.lastAccessTime()?.let { ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of("UTC")) }
    }

    val entryPosixPermissions = if (options.readMetadata) {
      WalkDirectoryEntryPosixImpl.Permissions(
        owner = Files.getAttribute(currentItem, "unix:uid", LinkOption.NOFOLLOW_LINKS) as Int,
        group = Files.getAttribute(currentItem, "unix:gid", LinkOption.NOFOLLOW_LINKS) as Int,
        mask = convertPermissionsToMask(sourceAttrs.permissions()),
        permissionsSet = sourceAttrs.permissions()
      )
    }
    else {
      null
    }

    val currentPathAsEel = EelPath.parse(currentItem.toString(), descriptor)

    if (sourceAttrs.isSymbolicLink) {
      if (options.yieldSymlinks) {
        val symlinkTarget = Files.readSymbolicLink(currentItem)
        val symlinkType = if (symlinkTarget.isAbsolute) {
          WalkDirectoryEntryPosixImpl.SymlinkAbsolute(EelPath.parse(symlinkTarget.toString(), descriptor))
        }
        else {
          WalkDirectoryEntryPosixImpl.SymlinkRelative(symlinkTarget.toString())
        }
        val entry = WalkDirectoryEntryPosixImpl(
          path = currentPathAsEel,
          type = symlinkType,
          permissions = entryPosixPermissions,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          attributes = WalkDirectoryEntryPosixImpl.Attributes
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    else if (sourceAttrs.isDirectory) {
      if (options.yieldDirectories) {
        val entry = WalkDirectoryEntryPosixImpl(
          path = currentPathAsEel,
          type = WalkDirectoryEntryPosixImpl.Directory,
          permissions = entryPosixPermissions,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          attributes = WalkDirectoryEntryPosixImpl.Attributes
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    else if (sourceAttrs.isRegularFile) {
      if (options.yieldRegularFiles) {
        val hash = if (!options.fileContentsHash) {
          null
        }
        else if (sourceAttrs.size() > 0) {
          FileChannel.open(currentItem, StandardOpenOption.READ).use { fileChannel ->
            val buffer = fileChannel.map(
              FileChannel.MapMode.READ_ONLY,
              0,
              sourceAttrs.size(),
            )
            Hashing.xxh3_64().hashBytesToLong(buffer.toByteArray())
          }
        }
        else {
          emptyFileHash
        }

        val entry = WalkDirectoryEntryPosixImpl(
          path = currentPathAsEel,
          type = WalkDirectoryEntryPosixImpl.Regular(hash),
          permissions = entryPosixPermissions,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          attributes = WalkDirectoryEntryPosixImpl.Attributes
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    else {
      if (options.yieldOtherFileTypes) {
        val entry = WalkDirectoryEntryPosixImpl(
          path = currentPathAsEel,
          type = WalkDirectoryEntryPosixImpl.Other,
          permissions = entryPosixPermissions,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          attributes = WalkDirectoryEntryPosixImpl.Attributes
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    return null
  }
}

abstract class WindowsNioBasedEelFileSystemApi(
  fs: FileSystem,
  override val user: EelUserWindowsInfo,
) : NioBasedEelFileSystemApi(fs), LocalEelFileSystemWindowsApi {

  override suspend fun getRootDirectories(): Collection<EelPath> =
    FileSystems.getDefault().rootDirectories.map { path ->
      EelPath.parse(path.toString(), LocalEelDescriptor)
    }

  override suspend fun listDirectoryWithAttrs(path: EelPath, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    Collection<Pair<String, EelWindowsFileInfo>>,
    EelFileSystemApi.ListDirectoryError> =
    super<NioBasedEelFileSystemApi>.listDirectoryWithAttrs(path, symlinkPolicy) as EelResult<Collection<Pair<String, EelWindowsFileInfo>>, EelFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: EelPath, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelWindowsFileInfo,
    EelFileSystemApi.StatError> =
    super<NioBasedEelFileSystemApi>.stat(path, symlinkPolicy) as EelResult<EelWindowsFileInfo, EelFileSystemApi.StatError>

  override suspend fun changeAttributes(
    path: EelPath,
    options: EelFileSystemApi.ChangeAttributesOptions,
  ): EelResult<Unit, EelFileSystemApi.ChangeAttributesError> =
    wrapIntoEelResult {
      val view = Files.getFileAttributeView(path.toNioPath(), DosFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
      check(view != null) { path.toString() }
      val oldAttrs = view.readAttributes()
      copyTimes(oldAttrs, options, view)

      // TODO File permissions for windows.
    }

  override suspend fun streamingWrite(chunks: Flow<ByteBuffer>, targetFileOpenOptions: EelFileSystemApi.WriteOptions): StreamingWriteResult = doStreamingWrite(chunks, targetFileOpenOptions)

  override suspend fun streamingRead(path: EelPath): Flow<StreamingReadResult> = doStreamingRead(path)

  override suspend fun walkDirectory(options: EelFileSystemApi.WalkDirectoryOptions): Flow<WalkDirectoryEntryResult> = flow {
    val rootDir = options.path.asNioPath()

    // the target path has to be a directory and needs to exist
    if (!rootDir.exists(LinkOption.NOFOLLOW_LINKS)) {
      val e = WalkDirectoryEntryResultImpl.Error(EelFsResultImpl.DoesNotExist(options.path, "provided path does not exist"))
      emit(e)
      return@flow
    }

    val emptyFileHash = Hashing.xxh3_64().hashStream().asLong
    val maxDepth = options.maxDepth

    when (options.traversalOrder) {
      EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryTraversalOrder.BFS -> {
        val q = ArrayDeque<Path>()
        q.addLast(rootDir)

        var currentDepth = 0
        while (q.isNotEmpty()) {
          val n = q.size
          repeat(n) {
            val currentItem = q.removeFirst()

            val sourceAttrs = currentItem.fileAttributesView<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
            walkDirectoryProcessFileWindows(currentItem, sourceAttrs, emptyFileHash, options)?.let { res -> emit(res) }

            // maxDepth < 0 means that there is not limit on the depth
            if (sourceAttrs.isDirectory && (maxDepth < 0 || currentDepth < maxDepth)) {
              var children = currentItem.listDirectoryEntries()
              children = when (options.entryOrder) {
                EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.RANDOM -> {
                  children
                }
                EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.ALPHABETICAL -> {
                  children.sortedBy { it.pathString }
                }
              }
              q.addAll(children)
            }
          }
          currentDepth += 1
        }
      }

      EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryTraversalOrder.DFS -> {
        when (options.entryOrder) {
          EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.RANDOM -> {
            // maxDepth < 0 means that there is not limit on the depth
            val maxDepth = if (maxDepth < 0) Integer.MAX_VALUE else maxDepth
            Files.walk(rootDir, maxDepth).use { pathStream ->
              for (path in pathStream) {
                val sourceAttrs = path.fileAttributesView<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
                walkDirectoryProcessFileWindows(path, sourceAttrs, emptyFileHash, options)?.let { res -> emit(res) }
              }
            }
          }
          EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder.ALPHABETICAL -> {
            val q = mutableListOf<Pair<Path, Int>>()
            q.addLast(Pair(rootDir, 0))

            while (q.isNotEmpty()) {
              val (currentItem, currDepth) = q.removeLast()

              val sourceAttrs = currentItem.fileAttributesView<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
              walkDirectoryProcessFileWindows(currentItem, sourceAttrs, emptyFileHash, options)?.let { res -> emit(res) }

              // maxDepth < 0 means that there is not limit on the depth
              if (sourceAttrs.isDirectory && (maxDepth < 0 || currDepth < maxDepth)) {
                val children = currentItem
                  .listDirectoryEntries()
                  .sortedByDescending { it.pathString }
                  .map { path -> Pair(path, currDepth + 1) }
                q.addAll(children)
              }
            }
          }
        }
      }
    }
  }

  private fun walkDirectoryProcessFileWindows(
    currentItem: Path,
    sourceAttrs: DosFileAttributes,
    emptyFileHash: Long,
    options: EelFileSystemApi.WalkDirectoryOptions,
  ): WalkDirectoryEntryResult? {
    var creationTime: ZonedDateTime? = null
    var lastModifiedTime: ZonedDateTime? = null
    var lastAccessTime: ZonedDateTime? = null
    if (options.readMetadata) {
      lastModifiedTime = sourceAttrs.lastModifiedTime()?.let { ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of("UTC")) }
      lastAccessTime = sourceAttrs.lastAccessTime()?.let { ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of("UTC")) }
      creationTime = sourceAttrs.creationTime()?.let { ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of("UTC")) }
    }

    val windowsFileAttributes = if (options.readMetadata) {
      WalkDirectoryEntryWindowsImpl.Attributes(
        isReadOnly = sourceAttrs.isReadOnly,
        isHidden = sourceAttrs.isHidden,
        isArchive = sourceAttrs.isArchive,
        isSystem = sourceAttrs.isSystem,
      )
    }
    else {
      null
    }

    val currentPathAsEel = EelPath.parse(currentItem.toString(), descriptor)

    if (sourceAttrs.isSymbolicLink) {
      if (options.yieldSymlinks) {
        val symlinkTarget = Files.readSymbolicLink(currentItem)
        val symlinkType = if (symlinkTarget.isAbsolute) {
          WalkDirectoryEntryWindowsImpl.SymlinkAbsolute(EelPath.parse(symlinkTarget.toString(), descriptor))
        }
        else {
          WalkDirectoryEntryWindowsImpl.SymlinkRelative(symlinkTarget.toString())
        }
        val entry = WalkDirectoryEntryWindowsImpl(
          path = currentPathAsEel,
          type = symlinkType,
          attributes = windowsFileAttributes,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          permissions = WalkDirectoryEntryWindowsImpl.Permissions
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    else if (sourceAttrs.isDirectory) {
      if (options.yieldDirectories) {
        val entry = WalkDirectoryEntryWindowsImpl(
          path = currentPathAsEel,
          type = WalkDirectoryEntryWindowsImpl.Directory,
          attributes = windowsFileAttributes,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          permissions = WalkDirectoryEntryWindowsImpl.Permissions
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    else if (sourceAttrs.isRegularFile) {
      if (options.yieldRegularFiles) {
        val hash = if (options.fileContentsHash) {
          null
        }
        else if (sourceAttrs.size() > 0) {
          FileChannel.open(currentItem, StandardOpenOption.READ).use { fileChannel ->
            val buffer = fileChannel.map(
              FileChannel.MapMode.READ_ONLY,
              0,
              sourceAttrs.size(),
            )
            val hash = Hashing.xxh3_64().hashBytesToLong(buffer.toByteArray())
            // NOTE: Windows requires explicit buffer cleaning
            ByteBufferUtil.cleanBuffer(buffer)
            hash
          }
        }
        else {
          emptyFileHash
        }

        val entry = WalkDirectoryEntryWindowsImpl(
          path = currentPathAsEel,
          type = WalkDirectoryEntryWindowsImpl.Regular(hash),
          attributes = windowsFileAttributes,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          permissions = WalkDirectoryEntryWindowsImpl.Permissions
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    else {
      if (options.yieldOtherFileTypes) {
        val entry = WalkDirectoryEntryWindowsImpl(
          path = currentPathAsEel,
          type = WalkDirectoryEntryWindowsImpl.Other,
          attributes = windowsFileAttributes,
          lastModifiedTime = lastModifiedTime,
          lastAccessTime = lastAccessTime,
          creationTime = creationTime,
          permissions = WalkDirectoryEntryWindowsImpl.Permissions
        )
        return WalkDirectoryEntryResultImpl.Ok(entry)
      }
    }
    return null
  }
}

private fun copyTimes(
  oldAttrs: BasicFileAttributes,
  options: EelFileSystemApi.ChangeAttributesOptions,
  view: BasicFileAttributeView,
) {
  val ctime = oldAttrs.creationTime()  // TODO ctime in Eel
  val mtime = options.modificationTime?.toFileTime() ?: oldAttrs.lastModifiedTime()
  val atime = options.accessTime?.toFileTime() ?: oldAttrs.lastAccessTime()
  view.setTimes(ctime, mtime, atime)
}

private fun EelFileSystemApi.TimeSinceEpoch.toFileTime(): FileTime =
  FileTime.from(Instant.ofEpochSecond(seconds.toLong(), nanoseconds.toLong()))

private fun getFileAttributes(child: Path, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelFileInfo =
  if (SystemInfoRt.isWindows) {
    getWindowsFileAttributes(child)
  }
  else {
    getPosixFileAttributes(child, symlinkPolicy)
  }

private fun getPosixFileAttributes(child: Path, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelPosixFileInfo {
  val s = child.fileSystem.provider().readAttributes(
    child, PosixFileAttributes::class.java,
    *(when (symlinkPolicy) {
      EelFileSystemApi.SymlinkPolicy.DO_NOT_RESOLVE -> arrayOf(LinkOption.NOFOLLOW_LINKS)
      EelFileSystemApi.SymlinkPolicy.JUST_RESOLVE, EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW -> arrayOf()
    })
  )
  return EelPosixFileInfoImpl(
    type = when {
      s.isRegularFile -> EelPosixFileInfoImpl.Regular(s.size())

      s.isDirectory -> EelPosixFileInfoImpl.Directory(EelFileInfo.CaseSensitivity.SENSITIVE)

      s.isSymbolicLink -> {
        // TODO Resolve symlinks if needed
        EelPosixFileInfoImpl.SymlinkUnresolved
      }

      else -> EelPosixFileInfoImpl.Other
    },

    permissions = EelPosixFileInfoImpl.Permissions(
      owner = Files.getAttribute(child, "unix:uid") as Int,
      group = Files.getAttribute(child, "unix:gid") as Int,
      mask = convertPermissionsToMask(s.permissions()),
    ),

    creationTime = ZonedDateTime.ofInstant(s.creationTime().toInstant(), ZoneId.systemDefault()),
    lastModifiedTime = ZonedDateTime.ofInstant(s.lastModifiedTime().toInstant(), ZoneId.systemDefault()),
    lastAccessTime = ZonedDateTime.ofInstant(s.lastAccessTime().toInstant(), ZoneId.systemDefault()),

    inodeDev = Files.getAttribute(child, "unix:dev").let { it as? Long ?: 0L },
    inodeIno = Files.getAttribute(child, "unix:ino").let { it as? Long ?: 0L },
  )
}

private fun getWindowsFileAttributes(child: Path): EelWindowsFileInfo {
  val s = Files.readAttributes(child, DosFileAttributes::class.java)
  return object : EelWindowsFileInfo {
    override val permissions: EelWindowsFileInfo.Permissions =
      object : EelWindowsFileInfo.Permissions {
        override val isReadOnly: Boolean = s.isReadOnly
        override val isHidden: Boolean = s.isHidden
        override val isArchive: Boolean = s.isArchive
        override val isSystem: Boolean = s.isSystem
      }


    override val type: EelFileInfo.Type = when {
      s.isRegularFile -> EelPosixFileInfoImpl.Regular(s.size())

      s.isDirectory -> EelPosixFileInfoImpl.Directory(EelFileInfo.CaseSensitivity.SENSITIVE)

      else -> EelPosixFileInfoImpl.Other
    }

    override val creationTime =
      ZonedDateTime.ofInstant(s.creationTime().toInstant(), ZoneId.systemDefault())
    override val lastModifiedTime =
      ZonedDateTime.ofInstant(s.lastModifiedTime().toInstant(), ZoneId.systemDefault())
    override val lastAccessTime =
      ZonedDateTime.ofInstant(s.lastAccessTime().toInstant(), ZoneId.systemDefault())
  }
}

private fun convertPermissionsToMask(permissions: Set<PosixFilePermission>): Int {
  var mask = 0
  if (PosixFilePermission.OWNER_READ in permissions) mask = mask or 0b100000000
  if (PosixFilePermission.OWNER_WRITE in permissions) mask = mask or 0b010000000
  if (PosixFilePermission.OWNER_EXECUTE in permissions) mask = mask or 0b001000000
  if (PosixFilePermission.GROUP_READ in permissions) mask = mask or 0b000100000
  if (PosixFilePermission.GROUP_WRITE in permissions) mask = mask or 0b000010000
  if (PosixFilePermission.GROUP_EXECUTE in permissions) mask = mask or 0b000001000
  if (PosixFilePermission.OTHERS_READ in permissions) mask = mask or 0b000000100
  if (PosixFilePermission.OTHERS_WRITE in permissions) mask = mask or 0b000000010
  if (PosixFilePermission.OTHERS_EXECUTE in permissions) mask = mask or 0b000000001
  return mask
}

private suspend fun doStreamingWrite(chunks: Flow<ByteBuffer>, targetFileOpenOptions: EelFileSystemApi.WriteOptions): StreamingWriteResult {
  var totalBytesWritten: Long = 0
  val path = targetFileOpenOptions.path
  val nioOptions = writeOptionsToNioOptions(targetFileOpenOptions)

  try {
    withContext(Dispatchers.IO) {
      Files.newByteChannel(
        path.asNioPath(),
        nioOptions
      ).use { channel ->
        chunks.collect { buffer ->
          while (buffer.hasRemaining()) {
            totalBytesWritten += channel.write(buffer)
          }
        }
      }
    }
  }
  catch (e: FileSystemException) {
    val err = when (e) {
      is NoSuchFileException -> EelFsResultImpl.DoesNotExist(path, e.message ?: "Target path does not exist")
      is FileAlreadyExistsException -> EelFsResultImpl.AlreadyExists(path, e.message ?: "Target path already exists")
      is AccessDeniedException -> EelFsResultImpl.NotFile(path, e.message
                                                                ?: "Target path is not a file, no permissions to write, or the path points to a directory")
      else -> EelFsResultImpl.Other(path, e.message ?: e.toString())
    }
    return StreamingWriteResultImpl.Error(err)
  }
  return StreamingWriteResultImpl.Ok(totalBytesWritten)
}

private fun doStreamingRead(path: EelPath): Flow<StreamingReadResult> =
  flow {
    try {
      Files.newByteChannel(path.asNioPath(), StandardOpenOption.READ).use { channel ->
        while (true) {
          // Buffer size chosen randomly
          val buffer = ByteBuffer.allocate(64 * 1024)
          val bytesRead = channel.read(buffer)
          if (bytesRead == -1) break
          buffer.flip()
          emit(StreamingReadResultImpl.Ok(buffer))
        }
      }
    }
    // IOException instead of FileSystemException because opening a directory for reading returns IOException
    catch (e: IOException) {
      val err = when (e) {
        is NoSuchFileException -> EelFsResultImpl.DoesNotExist(path, e.message ?: "Target path does not exist")
        is AccessDeniedException -> EelFsResultImpl.NotFile(path, e.message ?: "Target path is not a file or no permissions to read")
        else -> EelFsResultImpl.Other(path, e.message ?: e.toString())
      }
      emit(StreamingReadResultImpl.Error(err))
    }
  }.flowOn(Dispatchers.IO)

private fun writeOptionsToNioOptions(options: EelFileSystemApi.WriteOptions): MutableSet<StandardOpenOption> {
  val nioOptions = mutableSetOf<StandardOpenOption>(StandardOpenOption.WRITE)
  when (options.creationMode) {
    ALLOW_CREATE -> nioOptions += StandardOpenOption.CREATE
    ONLY_CREATE -> nioOptions += StandardOpenOption.CREATE_NEW
    ONLY_OPEN_EXISTING -> Unit
  }
  if (options.append) {
    nioOptions += StandardOpenOption.APPEND
  }
  if (options.truncateExisting) {
    nioOptions += StandardOpenOption.TRUNCATE_EXISTING
  }
  return nioOptions
}
