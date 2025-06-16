// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.EelUserWindowsInfo
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.fs.EelFileSystemApi.FileWriterCreationMode.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.exists
import kotlin.io.path.fileStore
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
    try {
      EelFsResultImpl.Ok(body())
    }
    catch (err: FileSystemException) {
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

  override suspend fun openForReading(path: EelPath): EelResult<
    EelOpenedFile.Reader,
    EelFileSystemApi.FileReaderError
    > =
    wrapIntoEelResult {
      val nioPath = path.toNioPath()
      val byteChannel: SeekableByteChannel = nioPath.fileSystem.provider().newByteChannel(nioPath, setOf(StandardOpenOption.READ))
      LocalEelOpenedFileReader(this, byteChannel, path)
    }

  override suspend fun openForWriting(options: EelFileSystemApi.WriteOptions): EelResult<
    EelOpenedFile.Writer,
    EelFileSystemApi.FileWriterError> =
    wrapIntoEelResult {
      val path = options.path
      val nioPath = path.toNioPath()
      val nioOptions = writeOptionsToNioOptions(options)
      val byteChannel: SeekableByteChannel = nioPath.fileSystem.provider().newByteChannel(nioPath, nioOptions)
      LocalEelOpenedFileWriter(this, byteChannel, path)
    }

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

  override suspend fun openForReadingAndWriting(options: EelFileSystemApi.WriteOptions): EelResult<
    EelOpenedFile.ReaderWriter,
    EelFileSystemApi.FileWriterError> =
    wrapIntoEelResult {
      val path = options.path
      val nioPath = path.toNioPath()
      val nioOptions = writeOptionsToNioOptions(options)
      nioOptions += StandardOpenOption.READ
      val byteChannel: SeekableByteChannel = nioPath.fileSystem.provider().newByteChannel(nioPath, nioOptions)
      object : EelOpenedFile.ReaderWriter, EelOpenedFile.Writer by LocalEelOpenedFileWriter(this, byteChannel, path) {
        override suspend fun read(buf: ByteBuffer): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
          doRead(this@NioBasedEelFileSystemApi, byteChannel, buf)

        override suspend fun read(
          buf: ByteBuffer,
          offset: Long,
        ): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
          doRead(this@NioBasedEelFileSystemApi, byteChannel, offset, buf)
      }
    }

  override suspend fun delete(path: EelPath, removeContent: Boolean): EelResult<Unit, EelFileSystemApi.DeleteError> =
    wrapIntoEelResult {
      Files.delete(path.toNioPath())
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

internal class LocalEelOpenedFileReader(
  private val eelFs: NioBasedEelFileSystemApi,
  private val byteChannel: SeekableByteChannel,
  private val path_: EelPath,
) : EelOpenedFile.Reader {
  override suspend fun read(buf: ByteBuffer): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
    doRead(eelFs, byteChannel, buf)

  override suspend fun read(buf: ByteBuffer, offset: Long): EelResult<
    ReadResult,
    EelOpenedFile.Reader.ReadError
    > =
    doRead(eelFs, byteChannel, offset, buf)

  override val path: EelPath = path_

  override suspend fun close(): EelResult<Unit, EelOpenedFile.CloseError> =
    doClose(eelFs, byteChannel, path_)

  override suspend fun tell(): EelResult<Long, EelOpenedFile.TellError> =
    doTell(eelFs, byteChannel, path_)

  override suspend fun seek(offset: Long, whence: EelOpenedFile.SeekWhence): EelResult<Long, EelOpenedFile.SeekError> =
    doSeek(eelFs, byteChannel, path_, whence, offset)

  override suspend fun stat(): EelResult<EelFileInfo, EelFileSystemApi.StatError> =
    eelFs.stat(path_, EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW)
}

private class LocalEelOpenedFileWriter(
  private val eelFs: NioBasedEelFileSystemApi,
  private val byteChannel: SeekableByteChannel,
  private val path_: EelPath,
) : EelOpenedFile.Writer {
  override suspend fun write(buf: ByteBuffer): EelResult<Int, EelOpenedFile.Writer.WriteError> =
    eelFs.wrapIntoEelResult {
      byteChannel.write(buf)
    }

  override suspend fun write(buf: ByteBuffer, pos: Long): EelResult<Int, EelOpenedFile.Writer.WriteError> =
    eelFs.wrapIntoEelResult {
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
    eelFs.wrapIntoEelResult {
      byteChannel.truncate(size)
    }

  override val path: EelPath = path_

  override suspend fun close(): EelResult<Unit, EelOpenedFile.CloseError> =
    doClose(eelFs, byteChannel, path_)

  override suspend fun tell(): EelResult<Long, EelOpenedFile.TellError> =
    doTell(eelFs, byteChannel, path_)

  override suspend fun seek(offset: Long, whence: EelOpenedFile.SeekWhence): EelResult<Long, EelOpenedFile.SeekError> =
    doSeek(eelFs, byteChannel, path_, whence, offset)

  override suspend fun stat(): EelResult<EelFileInfo, EelFileSystemApi.StatError> =
    eelFs.stat(path_, EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW)
}

private fun doRead(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  buf: ByteBuffer,
): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
  eelFs.wrapIntoEelResult {
    val read = byteChannel.read(buf)

    ReadResult.fromNumberOfReadBytes(read)
  }

private fun doRead(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  offset: Long,
  buf: ByteBuffer,
): EelResult<ReadResult, EelOpenedFile.Reader.ReadError> =
  eelFs.wrapIntoEelResult {
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
): EelResult<Long, EelOpenedFile.SeekError> = eelFs.wrapIntoEelResult {
  val newPosition = when (whence) {
    EelOpenedFile.SeekWhence.START -> offset
    EelOpenedFile.SeekWhence.CURRENT -> byteChannel.position() + offset
    EelOpenedFile.SeekWhence.END -> byteChannel.size() - offset
  }
  byteChannel.position(newPosition)
  newPosition
}

private fun doTell(eelFs: NioBasedEelFileSystemApi, byteChannel: SeekableByteChannel, path: EelPath): EelResult<Long, EelOpenedFile.TellError> =
  eelFs.wrapIntoEelResult {
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

  override suspend fun readFully(path: EelPath, limit: ULong, overflowPolicy: EelFileSystemApi.OverflowPolicy): EelResult<EelFileSystemApi.FullReadResult, EelFileSystemApi.FullReadError> {
    TODO("Not yet implemented")
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

  override suspend fun readFully(path: EelPath, limit: ULong, overflowPolicy: EelFileSystemApi.OverflowPolicy): EelResult<EelFileSystemApi.FullReadResult, EelFileSystemApi.FullReadError> {
    TODO("Not yet implemented")
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