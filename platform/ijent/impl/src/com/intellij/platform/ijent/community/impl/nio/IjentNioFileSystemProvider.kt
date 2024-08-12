// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ijent.community.impl.IjentFsResultImpl
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider.Companion.newFileSystemMap
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider.UnixFilePermissionBranch.*
import com.intellij.platform.ijent.fs.*
import com.intellij.platform.ijent.fs.IjentFileInfo.Type.*
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi.CreateDirectoryException
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi.CreateSymbolicLinkException
import com.intellij.platform.ijent.fs.IjentPosixFileInfo.Type.Symlink
import com.intellij.util.text.nullize
import com.sun.nio.file.ExtendedCopyOption
import java.io.IOException
import java.net.URI
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.nio.file.spi.FileSystemProvider.installedProviders
import java.util.concurrent.ExecutorService
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * This filesystem connects to particular IJent instances.
 *
 * A new filesystem can be created with [newFileSystem] and [newFileSystemMap].
 * The URL must have the scheme "ijent", and the unique identifier of a filesystem
 * is represented with an authority and a path.
 *
 * For accessing WSL use [com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystemProvider].
 */
class IjentNioFileSystemProvider : FileSystemProvider() {
  companion object {
    @JvmStatic
    fun getInstance(): IjentNioFileSystemProvider =
      installedProviders()
        .filterIsInstance<IjentNioFileSystemProvider>()
        .single()

    @JvmStatic
    fun newFileSystemMap(ijentFs: IjentFileSystemApi): MutableMap<String, *> =
      mutableMapOf(KEY_IJENT_FS to ijentFs)

    private const val SCHEME = "ijent"
    private const val KEY_IJENT_FS = "ijentFs"
  }

  @JvmInline
  internal value class CriticalSection<D : Any>(val hidden: D) {
    inline operator fun <T> invoke(body: D.() -> T): T =
      synchronized(hidden) {
        hidden.body()
      }
  }

  private val criticalSection = CriticalSection(object {
    val authorityRegistry: MutableMap<URI, IjentFileSystemApi> = hashMapOf()
  })

  override fun getScheme(): String = SCHEME

  override fun newFileSystem(uri: URI, env: MutableMap<String, *>): IjentNioFileSystem {
    @Suppress("NAME_SHADOWING") val uri = uri.normalize()
    typicalUriChecks(uri)
    val ijentFs =
      try {
        env[KEY_IJENT_FS] as IjentFileSystemApi
      }
      catch (err: Exception) {
        throw when (err) {
          is NullPointerException, is ClassCastException ->
            IllegalArgumentException("Invalid map. `IjentNioFileSystemProvider.newFileSystemMap` should be used for map creation.")
          else ->
            err
        }
      }
    val uriParts = getUriParts(uri)
    criticalSection {
      for (uriPart in uriParts) {
        if (uriPart in authorityRegistry) {
          throw FileSystemAlreadyExistsException(
            "`$uri` can't be registered because there's an already registered as IJent FS provider `$uriPart`"
          )
        }
      }
      authorityRegistry[uri] = ijentFs
    }
    return IjentNioFileSystem(this, uri)
  }

  private fun getUriParts(uri: URI): Collection<URI> = uri.path.asSequence()
    .mapIndexedNotNull { index, c -> if (c == '/') index else null }
    .map { URI(uri.scheme, uri.authority, uri.path.substring(0, it), null, null) }
    .plus(sequenceOf(uri))
    .toList()
    .asReversed()

  override fun newFileSystem(path: Path, env: MutableMap<String, *>): IjentNioFileSystem =
    newFileSystem(path.toUri(), env)

  override fun getFileSystem(uri: URI): IjentNioFileSystem {
    val uriParts = getUriParts(uri.normalize())
    val matchingUri = criticalSection {
      uriParts.firstOrNull { it in authorityRegistry }
    }
    if (matchingUri != null) {
      return IjentNioFileSystem(this, matchingUri)
    }
    else {
      throw FileSystemNotFoundException("`$uri` is not registered as IJent FS provider")
    }
  }

  override fun getPath(uri: URI): IjentNioPath {
    val nioFs = getFileSystem(uri)
    val relativeUri = nioFs.uri.relativize(uri)
    return nioFs.getPath(
      when (nioFs.ijentFs) {
        is IjentFileSystemPosixApi -> relativeUri.path.nullize() ?: "/"
        is IjentFileSystemWindowsApi -> relativeUri.path.trimStart('/')  // TODO Check that uri.path contains the drive letter.
      }
    )
  }

  override fun newByteChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): SeekableByteChannel =
    newFileChannel(path, options, *attrs)

  override fun newFileChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>?): FileChannel {
    ensureIjentNioPath(path)
    require(path.ijentPath is IjentPath.Absolute)
    // TODO Handle options and attrs
    val fs = path.nioFs

    require(!(READ in options && APPEND in options)) { "READ + APPEND not allowed" }
    require(!(APPEND in options && TRUNCATE_EXISTING in options)) { "APPEND + TRUNCATE_EXISTING not allowed" }

    return if (WRITE in options || APPEND in options) {
      if (DELETE_ON_CLOSE in options) TODO("WRITE + CREATE_NEW")
      if (LinkOption.NOFOLLOW_LINKS in options) TODO("WRITE + NOFOLLOW_LINKS")

      val writeOptions = IjentFileSystemApi.writeOptionsBuilder(path.ijentPath)
        .append(APPEND in options)
        .truncateExisting(TRUNCATE_EXISTING in options)
        .creationMode(when {
                        CREATE_NEW in options -> IjentFileSystemApi.FileWriterCreationMode.ONLY_CREATE
                        CREATE in options -> IjentFileSystemApi.FileWriterCreationMode.ALLOW_CREATE
                        else -> IjentFileSystemApi.FileWriterCreationMode.ONLY_OPEN_EXISTING
                      })

      fsBlocking {
        if (READ in options) {
          IjentNioFileChannel.createReadingWriting(fs, writeOptions)
        }
        else {
          IjentNioFileChannel.createWriting(fs, writeOptions)
        }
      }
    }
    else {
      if (CREATE in options) TODO("READ + CREATE")
      if (CREATE_NEW in options) TODO("READ + CREATE_NEW")
      if (DELETE_ON_CLOSE in options) TODO("READ + CREATE_NEW")
      if (LinkOption.NOFOLLOW_LINKS in options) TODO("READ + NOFOLLOW_LINKS")

      fsBlocking {
        IjentNioFileChannel.createReading(fs, path.ijentPath)
      }
    }
  }

  override fun newDirectoryStream(dir: Path, pathFilter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> {
    ensureIjentNioPath(dir)
    val nioFs = dir.nioFs

    return fsBlocking {
      // TODO listDirectoryWithAttrs+sun.nio.fs.BasicFileAttributesHolder
      val childrenNames = nioFs.ijentFs.listDirectory(ensurePathIsAbsolute(dir.ijentPath)).getOrThrowFileSystemException()

      val nioPathList = childrenNames.asSequence()
        .map { childName ->
          IjentNioPath(dir.ijentPath.getChild(childName).getOrThrow(), nioFs)
        }
        .filter { nioPath ->
          pathFilter?.accept(nioPath) != false
        }
        .toMutableList()

      object : DirectoryStream<Path> {
        // The compiler doesn't (didn't?) allow to relax types here.
        override fun iterator(): MutableIterator<Path> = nioPathList.iterator()
        override fun close(): Unit = Unit
      }
    }
  }

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) {
    ensureIjentNioPath(dir)
    val path = dir.ijentPath
    try {
      ensurePathIsAbsolute(path)
    }
    catch (e: IllegalArgumentException) {
      throw IOException(e)
    }
    try {
      fsBlocking {
        when (val fsApi = dir.nioFs.ijentFs) {
          is IjentFileSystemPosixApi -> fsApi.createDirectory(path, emptyList())
          is IjentFileSystemWindowsApi -> TODO()
        }
      }
    }
    catch (e: CreateDirectoryException) {
      when (e) {
        is CreateDirectoryException.DirAlreadyExists,
        is CreateDirectoryException.FileAlreadyExists,
          -> throw FileAlreadyExistsException(dir.toString())
        is CreateDirectoryException.ParentNotFound -> throw NoSuchFileException(dir.toString(), null, "Parent directory not found")
        else -> throw IOException(e)
      }
    }
  }

  override fun delete(path: Path) {
    ensureIjentNioPath(path)
    if (path.ijentPath !is IjentPath.Absolute) {
      throw FileSystemException(path.toString(), null, "Path is not absolute")
    }
    fsBlocking {
      try {
        path.nioFs.ijentFs.delete(path.ijentPath as IjentPath.Absolute,false, false)
      }
      catch (e: IjentFileSystemApi.DeleteException) {
        e.throwFileSystemException()
      }
    }
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption) {
    if (StandardCopyOption.ATOMIC_MOVE in options) {
      throw UnsupportedOperationException("Unsupported copy option")
    }
    ensureIjentNioPath(source)
    ensureIjentNioPath(target)
    val sourcePath = source.ijentPath
    val targetPath = target.ijentPath
    ensurePathIsAbsolute(sourcePath)
    ensurePathIsAbsolute(targetPath)

    val fs = source.nioFs.ijentFs

    val copyOptions = IjentFileSystemApi.copyOptionsBuilder(sourcePath, targetPath)
    copyOptions.followLinks(true)

    for (option in options) {
      when (option) {
        StandardCopyOption.REPLACE_EXISTING -> copyOptions.replaceExisting(true)
        StandardCopyOption.COPY_ATTRIBUTES -> copyOptions.preserveAttributes(true)
        ExtendedCopyOption.INTERRUPTIBLE -> copyOptions.interruptible(true)
        LinkOption.NOFOLLOW_LINKS -> copyOptions.followLinks(false)
        else -> {
          thisLogger().warn("Unknown copy option: $option. This option will be ignored.")
        }
      }
    }

    fsBlocking {
      try {
        fs.copy(copyOptions)
      } catch (e : IjentFileSystemApi.CopyException) {
        e.throwFileSystemException()
      }
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    ensureIjentNioPath(source)
    ensureIjentNioPath(target)
    val sourcePath = source.ijentPath
    val targetPath = target.ijentPath
    ensurePathIsAbsolute(sourcePath)
    ensurePathIsAbsolute(targetPath)
    return fsBlocking {
      try {
        source.nioFs.ijentFs.move(
          sourcePath,
          targetPath,
          replaceExisting = true,
          followLinks = LinkOption.NOFOLLOW_LINKS !in options)
      } catch (e : IjentFileSystemApi.MoveException) {
        e.throwFileSystemException()
      }
    }
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    ensureIjentNioPath(path)
    ensureIjentNioPath(path2)
    val nioFs = path.nioFs

    return fsBlocking {
      nioFs.ijentFs.sameFile(ensurePathIsAbsolute(path.ijentPath), ensurePathIsAbsolute(path2.ijentPath))
    }
      .getOrThrowFileSystemException()
  }

  override fun isHidden(path: Path): Boolean {
    TODO("Not yet implemented")
  }

  override fun getFileStore(path: Path): FileStore =
    IjentNioFileStore(ensureIjentNioPath(path).nioFs.ijentFs)

  private enum class UnixFilePermissionBranch { OWNER, GROUP, OTHER }

  override fun checkAccess(path: Path, vararg modes: AccessMode) {
    val fs = ensureIjentNioPath(path).nioFs
    fsBlocking {
      when (val ijentFs = fs.ijentFs) {
        is IjentFileSystemPosixApi -> {
          // According to the javadoc, this method must follow symlinks.
          val fileInfo = ijentFs.stat(ensurePathIsAbsolute(path.ijentPath), resolveSymlinks = true).getOrThrowFileSystemException()
          // Inspired by sun.nio.fs.UnixFileSystemProvider#checkAccess
          val filePermissionBranch = when {
            ijentFs.user.uid == fileInfo.permissions.owner -> OWNER
            ijentFs.user.gid == fileInfo.permissions.group -> GROUP
            else -> OTHER
          }

          if (AccessMode.READ in modes) {
            val canRead = when (filePermissionBranch) {
              OWNER -> fileInfo.permissions.ownerCanRead
              GROUP -> fileInfo.permissions.groupCanRead
              OTHER -> fileInfo.permissions.otherCanRead
            }
            if (!canRead) {
              (IjentFsResultImpl.PermissionDenied(path.ijentPath, "Permission denied: read") as IjentFsError).throwFileSystemException()
            }
          }
          if (AccessMode.WRITE in modes) {
            val canWrite = when (filePermissionBranch) {
              OWNER -> fileInfo.permissions.ownerCanWrite
              GROUP -> fileInfo.permissions.groupCanWrite
              OTHER -> fileInfo.permissions.otherCanWrite
            }
            if (!canWrite) {
              (IjentFsResultImpl.PermissionDenied(path.ijentPath, "Permission denied: write") as IjentFsError).throwFileSystemException()
            }
          }
          if (AccessMode.EXECUTE in modes) {
            val canExecute = when (filePermissionBranch) {
              OWNER -> fileInfo.permissions.ownerCanExecute
              GROUP -> fileInfo.permissions.groupCanExecute
              OTHER -> fileInfo.permissions.otherCanExecute
            }
            if (!canExecute) {
              (IjentFsResultImpl.PermissionDenied(path.ijentPath, "Permission denied: execute") as IjentFsError).throwFileSystemException()
            }
          }
        }
        is IjentFileSystemWindowsApi -> TODO()
      }
    }
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path, type: Class<V>?, vararg options: LinkOption?): V {
    TODO("Not yet implemented")
  }

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    val fs = ensureIjentNioPath(path).nioFs

    val result = when (val ijentFs = fs.ijentFs) {
      is IjentFileSystemPosixApi ->
        IjentNioPosixFileAttributes(fsBlocking {
          statPosix(path.ijentPath, ijentFs, LinkOption.NOFOLLOW_LINKS in options)
        })

      is IjentFileSystemWindowsApi -> TODO()
    }

    @Suppress("UNCHECKED_CAST")
    return result as A
  }

  private tailrec suspend fun statPosix(path: IjentPath, fsApi: IjentFileSystemPosixApi, resolveSymlinks: Boolean): IjentPosixFileInfo {
    val stat = fsApi.stat(ensurePathIsAbsolute(path), resolveSymlinks = resolveSymlinks).getOrThrowFileSystemException()
    return when (val type = stat.type) {
      is Directory, is Other, is Regular, is Symlink.Unresolved -> stat
      is Symlink.Resolved -> statPosix(type.result, fsApi, resolveSymlinks)
    }
  }

  override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption): MutableMap<String, Any> {
    TODO("Not yet implemented")
  }

  override fun setAttribute(path: Path, attribute: String?, value: Any?, vararg options: LinkOption?) {
    TODO("Not yet implemented")
  }

  override fun newAsynchronousFileChannel(
    path: Path?,
    options: MutableSet<out OpenOption>?,
    executor: ExecutorService?,
    vararg attrs: FileAttribute<*>?,
  ): AsynchronousFileChannel {
    TODO("Not yet implemented")
  }

  override fun createSymbolicLink(link: Path, target: Path, vararg attrs: FileAttribute<*>?) {
    if (attrs.isNotEmpty()) {
      throw UnsupportedOperationException("Attributes are not supported for symbolic links")
    }

    val fs = ensureIjentNioPath(link).nioFs
    val linkPath = ensurePathIsAbsolute(link.ijentPath)

    require(ensureIjentNioPath(target).nioFs == fs) {
      "Can't create symlinks between different file systems"
    }

    try {
      fsBlocking {
        when (val ijentFs = fs.ijentFs) {
          is IjentFileSystemPosixApi -> ijentFs.createSymbolicLink(target.ijentPath, linkPath)
          is IjentFileSystemWindowsApi -> TODO("Symbolic links are not supported on Windows")
        }
      }
    }
    catch (e: CreateSymbolicLinkException) {
      e.throwFileSystemException()
    }
  }

  override fun createLink(link: Path?, existing: Path?) {
    TODO("Not yet implemented")
  }

  override fun readSymbolicLink(link: Path?): Path {
    TODO("Not yet implemented")
  }

  internal fun close(uri: URI) {
    criticalSection {
      authorityRegistry.remove(uri)
    }
  }

  internal fun ijentFsApi(uri: URI): IjentFileSystemApi? =
    criticalSection {
      authorityRegistry[uri]
    }

  @OptIn(ExperimentalContracts::class)
  private fun ensureIjentNioPath(path: Path): IjentNioPath {
    contract {
      returns() implies (path is IjentNioPath)
    }

    if (path !is IjentNioPath) {
      throw ProviderMismatchException("$path (${path.javaClass}) is not ${IjentNioPath::class.java.simpleName}")
    }

    return path
  }

  @OptIn(ExperimentalContracts::class)
  private fun ensurePathIsAbsolute(path: IjentPath): IjentPath.Absolute {
    contract {
      returns() implies (path is IjentPath.Absolute)
    }

    return when (path) {
      is IjentPath.Absolute -> path
      is IjentPath.Relative -> throw InvalidPathException(path.toString(), "Relative paths are not accepted here")
    }
  }

  private fun typicalUriChecks(uri: URI) {
    require(uri.authority.isNotEmpty())

    require(uri.scheme == scheme) { "${uri.scheme} != $scheme" }
    require(uri.query.isNullOrEmpty()) { uri.query }
    require(uri.fragment.isNullOrEmpty()) { uri.fragment }
  }
}