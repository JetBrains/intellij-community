// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.fs

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.EelUserWindowsInfo
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.fs.EelFileSystemApi.ChangeAttributesException
import com.intellij.platform.eel.fs.EelFileSystemApi.FileWriterCreationMode.*
import com.intellij.platform.eel.fs.EelFileSystemPosixApi.CreateDirectoryException
import com.intellij.platform.eel.fs.EelFileSystemPosixApi.CreateSymbolicLinkException
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.EelFsResultImpl
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.jetbrains.annotations.VisibleForTesting
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.exists
import kotlin.io.path.fileStore
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.streams.asSequence

abstract class NioBasedEelFileSystemApi(@VisibleForTesting val fs: FileSystem) : EelFileSystemApi {
  protected abstract val pathOs: EelPath.Absolute.OS

  override suspend fun userHome(): EelPath.Absolute? =
    EelPath.Absolute.parse(System.getProperty("user.home"), null)

  protected fun EelPath.toNioPath(): Path =
    fs.getPath(toString())

  override suspend fun getDiskInfo(path: EelPath.Absolute): EelResult<EelFileSystemApi.DiskInfo, EelFileSystemApi.DiskInfoError> {
    val store = path.toNioPath().fileStore()
    return EelFsResultImpl.Ok(EelFsResultImpl.DiskInfoImpl(
      totalSpace = store.totalSpace.toULong(),
      availableSpace = store.usableSpace.toULong(),
    ))
  }

  internal inline fun <T, reified E : EelFsError> wrapIntoEelResult(body: () -> T): EelResult<T, E> =
    try {
      EelFsResultImpl.Ok(body())
    }
    catch (err: FileSystemException) {
      val path =
        try {
          EelPath.Absolute.parse(err.file.toString(), pathOs)
        }
        catch (_: EelPathException) {
          EelPath.Absolute.parse(fs.rootDirectories.first().toString(), pathOs)
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
      EelFsResultImpl.Error(err as? E ?: EelFsResultImpl.Other(path, err.message!!) as E)
    }

  internal inline fun <reified E> wrapIntoEelException(body: () -> Unit) where E : EelFsIOException {
    try {
      body()
    }
    catch (err: FileSystemException) {
      val path =
        try {
          EelPath.Absolute.parse(err.file.toString(), pathOs)
        }
        catch (_: EelPathException) {
          EelPath.Absolute.parse(fs.rootDirectories.first().toString(), pathOs)
        }
      val cl = E::class
      val subclasses = cl.sealedSubclasses

      val iface = when (err) {
        is AccessDeniedException -> EelFsError.PermissionDenied::class
        is NoSuchFileException -> EelFsError.DoesNotExist::class
        is FileAlreadyExistsException -> EelFsError.AlreadyExists::class
        is NotDirectoryException -> EelFsError.NotDirectory::class

        is NotLinkException -> EelFsError.Other::class
        is FileSystemLoopException -> EelFsError.Other::class
        is DirectoryNotEmptyException -> EelFsError.Other::class
        is AtomicMoveNotSupportedException -> EelFsError.Other::class
        else -> EelFsError.Other::class
      }

      // would be more correct to use 'single' here, but some kinds of exceptions have multiple children -- like the wildcard "Other"
      val appropriateSubclass = subclasses.first { it.isSubclassOf(iface) }

      throw appropriateSubclass.primaryConstructor!!.call(path, err.message!!)
    }
  }

  override suspend fun listDirectory(path: EelPath.Absolute): EelResult<Collection<String>, EelFileSystemApi.ListDirectoryError> =
    wrapIntoEelResult {
      Files.list(path.toNioPath()).use { stream ->
        stream.asSequence().mapTo(mutableListOf()) { it.fileName.toString() }
      }
    }

  override suspend fun listDirectoryWithAttrs(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
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

  override suspend fun canonicalize(path: EelPath.Absolute): EelResult<EelPath.Absolute, EelFileSystemApi.CanonicalizeError> =
    wrapIntoEelResult {
      val realPath = path.toNioPath().toRealPath()
      EelPath.Absolute.parse(realPath.toString(), null)
    }

  override suspend fun stat(
    path: EelPath.Absolute,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<out EelFileInfo, EelFileSystemApi.StatError> =
    wrapIntoEelResult {
      // TODO symlinkPolicy
      getFileAttributes(path.toNioPath(), symlinkPolicy)
    }

  override suspend fun sameFile(source: EelPath.Absolute, target: EelPath.Absolute): EelResult<Boolean, EelFileSystemApi.SameFileError> =
    wrapIntoEelResult {
      Files.isSameFile(source.toNioPath(), target.toNioPath())
    }

  override suspend fun openForReading(path: EelPath.Absolute): EelResult<
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
        override suspend fun read(buf: ByteBuffer): EelResult<EelOpenedFile.Reader.ReadResult, EelOpenedFile.Reader.ReadError> =
          doRead(this@NioBasedEelFileSystemApi, byteChannel, buf)

        override suspend fun read(
          buf: ByteBuffer,
          offset: Long,
        ): EelResult<EelOpenedFile.Reader.ReadResult, EelOpenedFile.Reader.ReadError> =
          doRead(this@NioBasedEelFileSystemApi, byteChannel, offset, buf)
      }
    }

  override suspend fun delete(path: EelPath.Absolute, removeContent: Boolean) {
    wrapIntoEelException<EelFileSystemApi.DeleteException> {
      Files.delete(path.toNioPath())
    }
  }

  override suspend fun copy(options: EelFileSystemApi.CopyOptions) {
    wrapIntoEelException<EelFileSystemApi.CopyException> {
      Files.copy(
        options.source.toNioPath(),
        options.target.toNioPath(),
        *listOfNotNull(
          if (options.replaceExisting) StandardCopyOption.REPLACE_EXISTING else null,
          if (options.preserveAttributes) StandardCopyOption.COPY_ATTRIBUTES else null,
        ).toTypedArray(),
      )
    }
  }

  override suspend fun move(
    source: EelPath.Absolute,
    target: EelPath.Absolute,
    replaceExisting: EelFileSystemApi.ReplaceExistingDuringMove,
    followLinks: Boolean,
  ) {
    wrapIntoEelException<EelFileSystemApi.MoveException> {
      val sourceNioPath = source.toNioPath()
      val targetNioPath = target.toNioPath()
      if (targetNioPath.exists() && replaceExisting == EelFileSystemApi.ReplaceExistingDuringMove.DO_NOT_REPLACE) {
        throw EelFileSystemApi.MoveException.TargetAlreadyExists(target)
      }
      EelPathUtils.walkingTransfer(sourceNioPath, targetNioPath, true)
    }
  }
}

private class LocalEelOpenedFileReader(
  private val eelFs: NioBasedEelFileSystemApi,
  private val byteChannel: SeekableByteChannel,
  private val path_: EelPath.Absolute,
) : EelOpenedFile.Reader {
  override suspend fun read(buf: ByteBuffer): EelResult<EelOpenedFile.Reader.ReadResult, EelOpenedFile.Reader.ReadError> =
    doRead(eelFs, byteChannel, buf)

  override suspend fun read(buf: ByteBuffer, offset: Long): EelResult<
    EelOpenedFile.Reader.ReadResult,
    EelOpenedFile.Reader.ReadError
    > =
    doRead(eelFs, byteChannel, offset, buf)

  override val path: EelPath.Absolute = path_

  override suspend fun close() {
    doClose(eelFs, byteChannel, path_)
  }

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
  private val path_: EelPath.Absolute,
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

  override suspend fun flush() {
    // Nothing.
  }

  override suspend fun truncate(size: Long) {
    eelFs.wrapIntoEelException<EelOpenedFile.Writer.TruncateException> {
      byteChannel.truncate(size)
    }
  }

  override val path: EelPath.Absolute = path_

  override suspend fun close() {
    doClose(eelFs, byteChannel, path_)
  }

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
): EelResult<EelOpenedFile.Reader.ReadResult, EelOpenedFile.Reader.ReadError> =
  eelFs.wrapIntoEelResult {
    val read = byteChannel.read(buf)

    if (read >= 0) EelFsResultImpl.BytesReadImpl(read)
    else EelFsResultImpl.EOFImpl
  }

private fun doRead(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  offset: Long,
  buf: ByteBuffer,
): EelResult<EelOpenedFile.Reader.ReadResult, EelOpenedFile.Reader.ReadError> =
  eelFs.wrapIntoEelResult {
    val oldPosition = byteChannel.position()
    byteChannel.position(offset)
    val read = byteChannel.read(buf)
    byteChannel.position(oldPosition)

    if (read >= 0) EelFsResultImpl.BytesReadImpl(read)
    else EelFsResultImpl.EOFImpl
  }

private fun doSeek(
  eelFs: NioBasedEelFileSystemApi,
  byteChannel: SeekableByteChannel,
  path: EelPath.Absolute,
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

private fun doTell(eelFs: NioBasedEelFileSystemApi, byteChannel: SeekableByteChannel, path: EelPath.Absolute): EelResult<Long, EelOpenedFile.TellError> =
  eelFs.wrapIntoEelResult {
    byteChannel.position().toLong()
  }

private fun doClose(eelFs: NioBasedEelFileSystemApi, byteChannel: SeekableByteChannel, path: EelPath.Absolute) {
  eelFs.wrapIntoEelException<EelOpenedFile.CloseException> {
    byteChannel.close()
  }
}

abstract class PosixNioBasedEelFileSystemApi(
  fs: FileSystem,
  override val user: EelUserPosixInfo,
) : NioBasedEelFileSystemApi(fs), EelFileSystemPosixApi {
  override suspend fun createDirectory(path: EelPath.Absolute, attributes: List<EelFileSystemPosixApi.CreateDirAttributePosix>) {
    wrapIntoEelException<CreateDirectoryException> {
      Files.createDirectory(path.toNioPath())
    }
  }

  override suspend fun listDirectoryWithAttrs(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    Collection<Pair<String, EelPosixFileInfo>>,
    EelFileSystemApi.ListDirectoryError> =
    super.listDirectoryWithAttrs(path, symlinkPolicy) as EelResult<Collection<Pair<String, EelPosixFileInfo>>, EelFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelPosixFileInfo,
    EelFileSystemApi.StatError> =
    super.stat(path, symlinkPolicy) as EelResult<EelPosixFileInfo, EelFileSystemApi.StatError>

  override suspend fun changeAttributes(path: EelPath.Absolute, options: EelFileSystemApi.ChangeAttributesOptions) {
    wrapIntoEelException<ChangeAttributesException> {
      TODO("Not yet implemented")
      //Files.setAttribute(path.toNioPath(), )
    }
  }

  override suspend fun createSymbolicLink(target: EelPath, linkPath: EelPath.Absolute) {
    wrapIntoEelException<CreateSymbolicLinkException> {
      Files.createSymbolicLink(linkPath.toNioPath(), target.toNioPath())
    }
  }
}

abstract class WindowsNioBasedEelFileSystemApi(
  fs: FileSystem,
  override val user: EelUserWindowsInfo,
) : NioBasedEelFileSystemApi(fs), EelFileSystemWindowsApi {
  override val pathOs: EelPath.Absolute.OS = EelPath.Absolute.OS.WINDOWS

  override suspend fun getRootDirectories(): Collection<EelPath.Absolute> =
    FileSystems.getDefault().rootDirectories.map { path ->
      EelPath.Absolute.parse(path.toString(), pathOs)
    }

  override suspend fun listDirectoryWithAttrs(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    Collection<Pair<String, EelWindowsFileInfo>>,
    EelFileSystemApi.ListDirectoryError> =
    super.listDirectoryWithAttrs(path, symlinkPolicy) as EelResult<Collection<Pair<String, EelWindowsFileInfo>>, EelFileSystemApi.ListDirectoryError>

  override suspend fun stat(path: EelPath.Absolute, symlinkPolicy: EelFileSystemApi.SymlinkPolicy): EelResult<
    EelWindowsFileInfo,
    EelFileSystemApi.StatError> =
    super.stat(path, symlinkPolicy) as EelResult<EelWindowsFileInfo, EelFileSystemApi.StatError>

  override suspend fun changeAttributes(path: EelPath.Absolute, options: EelFileSystemApi.ChangeAttributesOptions) {
    wrapIntoEelException<ChangeAttributesException> {
      TODO("Not yet implemented")
      //Files.setAttribute(path.toNioPath(), )
    }
  }
}

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