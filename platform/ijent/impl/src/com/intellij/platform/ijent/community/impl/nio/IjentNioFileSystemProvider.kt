// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2.FetchAttributesFilter
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.fs.EelFileInfo.Type.*
import com.intellij.platform.eel.fs.EelFileSystemApi.ReplaceExistingDuringMove.*
import com.intellij.platform.eel.fs.EelFileSystemPosixApi.CreateDirectoryException
import com.intellij.platform.eel.fs.EelFileSystemPosixApi.CreateSymbolicLinkException
import com.intellij.platform.eel.fs.EelPosixFileInfo.Type.Symlink
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.getOrThrow
import com.intellij.platform.eel.provider.EelFsResultImpl
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider.Companion.newFileSystemMap
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider.UnixFilePermissionBranch.*
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import com.intellij.platform.ijent.fs.IjentFileSystemWindowsApi
import com.intellij.util.io.PosixFilePermissionsUtil
import com.intellij.util.text.nullize
import com.sun.nio.file.ExtendedCopyOption
import java.io.IOException
import java.net.URI
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.*
import java.nio.file.spi.FileSystemProvider
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

  override fun newFileChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): FileChannel {
    ensureIjentNioPath(path)
    require(path.eelPath is EelPath.Absolute)
    validateAttributes(attrs)
    // TODO Handle options and attrs
    val fs = path.nioFs

    require(!(READ in options && APPEND in options)) { "READ + APPEND not allowed" }
    require(!(APPEND in options && TRUNCATE_EXISTING in options)) { "APPEND + TRUNCATE_EXISTING not allowed" }

    return if (WRITE in options || APPEND in options) {
      if (DELETE_ON_CLOSE in options) TODO("WRITE + CREATE_NEW")
      if (LinkOption.NOFOLLOW_LINKS in options) TODO("WRITE + NOFOLLOW_LINKS")

      val writeOptions = EelFileSystemApi.writeOptionsBuilder(path.eelPath)
        .append(APPEND in options)
        .truncateExisting(TRUNCATE_EXISTING in options)
        .creationMode(when {
                        CREATE_NEW in options -> EelFileSystemApi.FileWriterCreationMode.ONLY_CREATE
                        CREATE in options -> EelFileSystemApi.FileWriterCreationMode.ALLOW_CREATE
                        else -> EelFileSystemApi.FileWriterCreationMode.ONLY_OPEN_EXISTING
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
        IjentNioFileChannel.createReading(fs, path.eelPath)
      }
    }
  }

  private fun validateAttributes(attrs: Array<out FileAttribute<*>>) {
    for (attribute in attrs) {
      val (viewName, paramName) = parseAttributesParameter(attribute.name())
      if (viewName == "posix") {
        if (paramName == listOf("permissions")) {
          continue
        }
      }
      throw UnsupportedOperationException("Cannot create file with atomically set parameter $paramName")
    }
  }

  override fun newDirectoryStream(dir: Path, pathFilter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> {
    ensureIjentNioPath(dir)
    val nioFs = dir.nioFs

    return fsBlocking {
      val notFilteredPaths =
        if (pathFilter is FetchAttributesFilter) {
          nioFs.ijentFs
            .listDirectoryWithAttrs(ensurePathIsAbsolute(dir.eelPath), EelFileSystemApi.SymlinkPolicy.DO_NOT_RESOLVE)
            .getOrThrowFileSystemException()
            .asSequence()
            .map { (childName, childStat) ->
              val childIjentPath = dir.eelPath.getChild(childName).getOrThrow()
              val childAttrs = when (childStat) {
                is EelPosixFileInfo -> IjentNioPosixFileAttributes(childStat)
                is EelWindowsFileInfo -> TODO()
              }
              IjentNioPath(childIjentPath, nioFs, childAttrs)
            }
        }
        else {
          nioFs.ijentFs
            .listDirectory(ensurePathIsAbsolute(dir.eelPath))
            .getOrThrowFileSystemException()
            .asSequence()
            .map { childName ->
              val childIjentPath = dir.eelPath.getChild(childName).getOrThrow()
              IjentNioPath(childIjentPath, nioFs, null)
            }
        }
      val nioPathList = notFilteredPaths.filterTo(mutableListOf()) { nioPath ->
        pathFilter?.accept(nioPath) != false
      }

      object : DirectoryStream<Path> {
        // The compiler doesn't (didn't?) allow to relax types here.
        override fun iterator(): MutableIterator<Path> = nioPathList.iterator()
        override fun close(): Unit = Unit
      }
    }
  }

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) {
    ensureIjentNioPath(dir)
    val path = dir.eelPath
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
    if (path.eelPath !is EelPath.Absolute) {
      throw FileSystemException(path.toString(), null, "Path is not absolute")
    }
    fsBlocking {
      try {
        path.nioFs.ijentFs.delete(path.eelPath, false)
      }
      catch (e: EelFileSystemApi.DeleteException) {
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
    val sourcePath = source.eelPath
    val targetPath = target.eelPath
    ensurePathIsAbsolute(sourcePath)
    ensurePathIsAbsolute(targetPath)

    val fs = source.nioFs.ijentFs

    val copyOptions = EelFileSystemApi.copyOptionsBuilder(sourcePath, targetPath)
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
      }
      catch (e: EelFileSystemApi.CopyException) {
        e.throwFileSystemException()
      }
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    ensureIjentNioPath(source)
    ensureIjentNioPath(target)
    val sourcePath = source.eelPath
    val targetPath = target.eelPath
    ensurePathIsAbsolute(sourcePath)
    ensurePathIsAbsolute(targetPath)
    return fsBlocking {
      try {
        source.nioFs.ijentFs.move(
          sourcePath,
          targetPath,
          replaceExisting = run {
            // This code may change when implementing Windows support.
            when {
              StandardCopyOption.ATOMIC_MOVE in options -> DO_NOT_REPLACE_DIRECTORIES
              StandardCopyOption.REPLACE_EXISTING in options -> REPLACE_EVERYTHING
              else -> DO_NOT_REPLACE
            }
          },
          // In NIO, `move` does not follow links. This behavior is not influenced by the presense of NOFOLLOW_LINKS in CopyOptions
          // See java.nio.file.CopyMoveHelper.convertMoveToCopyOptions
          followLinks = false)
      }
      catch (e: EelFileSystemApi.MoveException) {
        e.throwFileSystemException()
      }
    }
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    ensureIjentNioPath(path)
    ensureIjentNioPath(path2)
    val nioFs = path.nioFs

    return fsBlocking {
      nioFs.ijentFs.sameFile(ensurePathIsAbsolute(path.eelPath), ensurePathIsAbsolute(path2.eelPath))
    }
      .getOrThrowFileSystemException()
  }

  override fun isHidden(path: Path): Boolean {
    TODO("Not yet implemented")
  }

  override fun getFileStore(path: Path): FileStore {
    val path = ensureIjentNioPath(path)
    ensurePathIsAbsolute(path.eelPath)
    return IjentNioFileStore(path.eelPath, path.nioFs.ijentFs)
  }

  private enum class UnixFilePermissionBranch { OWNER, GROUP, OTHER }

  override fun checkAccess(path: Path, vararg modes: AccessMode) {
    val fs = ensureIjentNioPath(path).nioFs
    fsBlocking {
      when (val ijentFs = fs.ijentFs) {
        is IjentFileSystemPosixApi -> {
          val fileInfo = ijentFs
            // According to the Javadoc, this method must follow symlinks.
            .stat(ensurePathIsAbsolute(path.eelPath), EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW)
            .getOrThrowFileSystemException()
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
              (EelFsResultImpl.PermissionDenied(path.eelPath, "Permission denied: read") as EelFsError).throwFileSystemException()
            }
          }
          if (AccessMode.WRITE in modes) {
            val canWrite = when (filePermissionBranch) {
              OWNER -> fileInfo.permissions.ownerCanWrite
              GROUP -> fileInfo.permissions.groupCanWrite
              OTHER -> fileInfo.permissions.otherCanWrite
            }
            if (!canWrite) {
              (EelFsResultImpl.PermissionDenied(path.eelPath, "Permission denied: write") as EelFsError).throwFileSystemException()
            }
          }
          if (AccessMode.EXECUTE in modes) {
            val canExecute = when (filePermissionBranch) {
              OWNER -> fileInfo.permissions.ownerCanExecute
              GROUP -> fileInfo.permissions.groupCanExecute
              OTHER -> fileInfo.permissions.otherCanExecute
            }
            if (!canExecute) {
              (EelFsResultImpl.PermissionDenied(path.eelPath, "Permission denied: execute") as EelFsError).throwFileSystemException()
            }
          }
        }
        is IjentFileSystemWindowsApi -> TODO()
      }
    }
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path, type: Class<V>?, vararg options: LinkOption): V? {
    ensureIjentNioPath(path)
    ensurePathIsAbsolute(path.eelPath)
    if (type == BasicFileAttributeView::class.java) {
      val basicAttributes = readAttributes<BasicFileAttributes>(path, BasicFileAttributes::class.java, *options)
      @Suppress("UNCHECKED_CAST")
      return IjentNioBasicFileAttributeView(path.nioFs.ijentFs, path.eelPath, path, basicAttributes) as V
    }
    val nioFs = ensureIjentNioPath(path).nioFs
    when (nioFs.ijentFs) {
      is IjentFileSystemPosixApi -> {
        if (type == PosixFileAttributeView::class.java) {
          val posixAttributes = readAttributes<PosixFileAttributes>(path, PosixFileAttributes::class.java, *options)
          @Suppress("UNCHECKED_CAST")
          return IjentNioPosixFileAttributeView(path.nioFs.ijentFs, path.eelPath, path, posixAttributes) as V
        }
        else {
          return null
        }
      }
      is IjentFileSystemWindowsApi -> TODO()
    }
  }


  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    val fs = ensureIjentNioPath(path).nioFs

    val linkPolicy = if (LinkOption.NOFOLLOW_LINKS in options) {
      EelFileSystemApi.SymlinkPolicy.DO_NOT_RESOLVE
    }
    else {
      EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW
    }

    val result = when (val ijentFs = fs.ijentFs) {
      is IjentFileSystemPosixApi ->
        IjentNioPosixFileAttributes(fsBlocking {
          ijentFs.stat(ensurePathIsAbsolute(path.eelPath), linkPolicy).getOrThrowFileSystemException()
        })

      is IjentFileSystemWindowsApi -> TODO()
    }

    @Suppress("UNCHECKED_CAST")
    return result as A
  }

  override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption): Map<String, Any> {
    val (viewName, requestedAttributes) = parseAttributesParameter(attributes)
    val rawMap = when (viewName) {
      "basic" -> {
        val basicAttributes = readAttributes(path, BasicFileAttributes::class.java, *options)
        mapOf(
          "lastModifiedTime" to basicAttributes.lastModifiedTime(),
          "lastAccessTime" to basicAttributes.lastAccessTime(),
          "creationTime" to basicAttributes.creationTime(),
          "size" to basicAttributes.size(),
          "isRegularFile" to basicAttributes.isRegularFile,
          "isDirectory" to basicAttributes.isDirectory,
          "isSymbolicLink" to basicAttributes.isSymbolicLink,
          "isOther" to basicAttributes.isOther,
          "fileKey" to basicAttributes.fileKey(),
        )
      }
      "posix" -> {
        val posixAttributes = readAttributes(path, PosixFileAttributes::class.java, *options)
        mapOf(
          "permissions" to posixAttributes.permissions(),
          "group" to posixAttributes.group(),
        )
      }
      else -> {
        throw UnsupportedOperationException("Unsupported file attribute view $attributes")
      }
    }
    return rawMap.filterKeys { requestedAttributes.contains(it) }
  }

  private fun parseAttributesParameter(parameter: String): Pair<String, List<String>> {
    val viewNameAndList = parameter.split(':')
    return if (viewNameAndList.size == 2) {
      viewNameAndList[0] to viewNameAndList[1].split(',')
    }
    else {
      "basic" to viewNameAndList[0].split(',')
    }
  }

  override fun setAttribute(path: Path, attribute: String, value: Any, vararg options: LinkOption?) {
    val (viewName, requestedAttributes) = parseAttributesParameter(attribute)
    val nioFs = ensureIjentNioPath(path).nioFs
    val eelPath = ensurePathIsAbsolute(path.eelPath)
    val builder = EelFileSystemApi.changeAttributesBuilder()
    when (viewName) {
      "basic" -> when (requestedAttributes.singleOrNull()) {
        "lastModifiedTime" -> builder.updateTime(EelFileSystemApi.ChangeAttributesOptions::modificationTime, value)
        "lastAccessTime" -> builder.updateTime(EelFileSystemApi.ChangeAttributesOptions::accessTime, value)
        "creationTime" -> value as FileTime // intentionally no-op, like in Java; but we need to throw CCE just in case
        else -> throw IllegalArgumentException("Unrecognized attribute: $attribute")
      }
      "posix" -> when (requestedAttributes.singleOrNull()) {
        "permissions" -> {
          value as Set<*> // ClassCastException is expected
          @Suppress("UNCHECKED_CAST") val mask = PosixFilePermissionsUtil.toUnixMode(value as Set<PosixFilePermission>)
          val permissions = EelPosixFileInfoImpl.Permissions(0, 0, mask)
          builder.permissions(permissions)
        }
        else -> throw IllegalArgumentException("Unrecognized attribute: $attribute")
      }
      else -> throw java.lang.IllegalArgumentException("Unrecognized attribute: $attribute")
    }
    try {
      fsBlocking {
        nioFs.ijentFs.changeAttributes(eelPath, builder)
      }
    }
    catch (e: EelFileSystemApi.ChangeAttributesException) {
      e.throwFileSystemException()
    }
  }

  override fun newAsynchronousFileChannel(
    path: Path?,
    options: MutableSet<out OpenOption>?,
    executor: ExecutorService?,
    vararg attrs: FileAttribute<*>?,
  ): AsynchronousFileChannel {
    TODO("Not yet implemented -> com.intellij.platform.ijent.functional.fs.TodoOperation.ASYNC_FILE_CHANNEL")
  }

  override fun createSymbolicLink(link: Path, target: Path, vararg attrs: FileAttribute<*>?) {
    if (attrs.isNotEmpty()) {
      throw UnsupportedOperationException("Attributes are not supported for symbolic links")
    }

    val fs = ensureIjentNioPath(link).nioFs
    val linkPath = ensurePathIsAbsolute(link.eelPath)

    require(ensureIjentNioPath(target).nioFs == fs) {
      "Can't create symlinks between different file systems"
    }

    try {
      fsBlocking {
        when (val ijentFs = fs.ijentFs) {
          is IjentFileSystemPosixApi -> ijentFs.createSymbolicLink(target.eelPath, linkPath)
          is IjentFileSystemWindowsApi -> TODO("Symbolic links are not supported on Windows")
        }
      }
    }
    catch (e: CreateSymbolicLinkException) {
      e.throwFileSystemException()
    }
  }

  override fun createLink(link: Path?, existing: Path?) {
    TODO("Not yet implemented -> com.intellij.platform.ijent.functional.fs.TodoOperation.HARD_LINK")
  }

  override fun readSymbolicLink(link: Path): Path {
    val fs = ensureIjentNioPath(link).nioFs
    val absolutePath = ensurePathIsAbsolute(link.eelPath)
    return fsBlocking {
      when (val ijentFs = fs.ijentFs) {
        is IjentFileSystemPosixApi -> when (val type = ijentFs.stat(absolutePath, EelFileSystemApi.SymlinkPolicy.JUST_RESOLVE).getOrThrowFileSystemException().type) {
          is Symlink.Resolved -> IjentNioPath(type.result, link.nioFs, null)
          is Directory, is Regular, is Other -> throw NotLinkException(link.toString())
          is Symlink.Unresolved -> error("Impossible, the link should be resolved")
        }
        is IjentFileSystemWindowsApi -> TODO()
      }
    }
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
  private fun ensurePathIsAbsolute(path: EelPath): EelPath.Absolute {
    contract {
      returns() implies (path is EelPath.Absolute)
    }

    return when (path) {
      is EelPath.Absolute -> path
      is EelPath.Relative -> throw InvalidPathException(path.toString(), "Relative paths are not accepted here")
    }
  }

  private fun typicalUriChecks(uri: URI) {
    require(uri.authority.isNotEmpty())

    require(uri.scheme == scheme) { "${uri.scheme} != $scheme" }
    require(uri.query.isNullOrEmpty()) { uri.query }
    require(uri.fragment.isNullOrEmpty()) { uri.fragment }
  }
}


internal fun EelFileSystemApi.ChangeAttributesOptions.updateTime(selector: EelFileSystemApi.ChangeAttributesOptions.(EelFileSystemApi.TimeSinceEpoch) -> Unit, obj: Any) {
  obj as FileTime // ClassCastException is expected
  val instant = obj.toInstant()
  selector(EelFileSystemApi.timeSinceEpoch(instant.epochSecond.toULong(), instant.nano.toUInt()))
}