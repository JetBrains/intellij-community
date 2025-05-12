// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2.FetchAttributesFilter
import com.intellij.platform.eel.directorySeparators
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.fs.EelFileInfo.Type.*
import com.intellij.platform.eel.fs.EelFileSystemApi.ReplaceExistingDuringMove.*
import com.intellij.platform.eel.fs.EelPosixFileInfo.Type.Symlink
import com.intellij.platform.eel.impl.fs.EelFsResultImpl
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.platform.eel.provider.utils.throwFileSystemException
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
    if (uri.authority.isNullOrEmpty()
        || uri.scheme != this.scheme
        || !uri.query.isNullOrEmpty()
        || !uri.fragment.isNullOrEmpty()) {
      throw UnsupportedOperationException(uri.toString() + " doesn't look like a proper URL for " + IjentNioFileSystemProvider::class.simpleName)
    }

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
    require(path is AbsoluteIjentNioPath)
    validateAttributes(attrs)
    // TODO Handle options and attrs
    val fs = path.nioFs

    require(!(READ in options && APPEND in options)) { "READ + APPEND not allowed" }
    require(!(APPEND in options && TRUNCATE_EXISTING in options)) { "APPEND + TRUNCATE_EXISTING not allowed" }

    return if (WRITE in options || APPEND in options) {
      if (DELETE_ON_CLOSE in options) TODO("WRITE + CREATE_NEW")
      if (LinkOption.NOFOLLOW_LINKS in options) TODO("WRITE + NOFOLLOW_LINKS")

      val writeOptions = EelFileSystemApi.WriteOptions.Builder(path.eelPath)
        .append(APPEND in options)
        .truncateExisting(TRUNCATE_EXISTING in options)
        .creationMode(when {
                        CREATE_NEW in options -> EelFileSystemApi.FileWriterCreationMode.ONLY_CREATE
                        CREATE in options -> EelFileSystemApi.FileWriterCreationMode.ALLOW_CREATE
                        else -> EelFileSystemApi.FileWriterCreationMode.ONLY_OPEN_EXISTING
                      })
        .build()

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
    ensureAbsoluteIjentNioPath(dir)
    val nioFs = dir.nioFs

    return fsBlocking {
      val notFilteredPaths =
        if (FetchAttributesFilter.isFetchAttributesFilter(pathFilter)) {
          nioFs.ijentFs
            .listDirectoryWithAttrs(dir.eelPath, EelFileSystemApi.SymlinkPolicy.DO_NOT_RESOLVE)
            .getOrThrowFileSystemException()
            .asSequence()
            .map { (childName, childStat) ->
              val childIjentPath = dir.eelPath.getChild(childName)
              val childAttrs = when (childStat) {
                is EelPosixFileInfo -> IjentNioPosixFileAttributes(childStat)
                is EelWindowsFileInfo -> TODO()
              }
              AbsoluteIjentNioPath(childIjentPath, nioFs, childAttrs)
            }
        }
        else {
          nioFs.ijentFs
            .listDirectory(dir.eelPath)
            .getOrThrowFileSystemException()
            .asSequence()
            .map { childName ->
              val childIjentPath = dir.eelPath.getChild(childName)
              AbsoluteIjentNioPath(childIjentPath, nioFs, null)
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
    try {
      ensureAbsoluteIjentNioPath(dir)
    }
    catch (e: IllegalArgumentException) {
      throw IOException(e)
    }
    val path = dir.eelPath
    fsBlocking {
      when (val fsApi = dir.nioFs.ijentFs) {
        is IjentFileSystemPosixApi -> fsApi.createDirectory(path, emptyList()).getOrThrowFileSystemException()
        is IjentFileSystemWindowsApi -> TODO()
      }
    }
  }

  override fun delete(path: Path) {
    ensureAbsoluteIjentNioPath(path)
    fsBlocking {
      path.nioFs.ijentFs.delete(path.eelPath, false).getOrThrowFileSystemException()
    }
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption) {
    if (StandardCopyOption.ATOMIC_MOVE in options) {
      throw UnsupportedOperationException("Unsupported copy option")
    }
    ensureAbsoluteIjentNioPath(source)
    ensureAbsoluteIjentNioPath(target)
    val sourcePath = source.eelPath
    val targetPath = target.eelPath

    val fs = source.nioFs.ijentFs

    val copyOptions = fs.copy(sourcePath, targetPath)
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
      copyOptions.getOrThrowFileSystemException()
    }
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    ensureAbsoluteIjentNioPath(source)
    ensureAbsoluteIjentNioPath(target)
    val sourcePath = source.eelPath
    val targetPath = target.eelPath
    return fsBlocking {
      source.nioFs.ijentFs.move(sourcePath, targetPath)
        .replaceExisting(
          // This code may change when implementing Windows support.
          when {
            StandardCopyOption.ATOMIC_MOVE in options -> DO_NOT_REPLACE_DIRECTORIES
            StandardCopyOption.REPLACE_EXISTING in options -> REPLACE_EVERYTHING
            else -> DO_NOT_REPLACE
          })
        // In NIO, `move` does not follow links. This behavior is not influenced by the presense of NOFOLLOW_LINKS in CopyOptions
        // See java.nio.file.CopyMoveHelper.convertMoveToCopyOptions
        .followLinks(false)
        .getOrThrowFileSystemException()
    }
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    ensureAbsoluteIjentNioPath(path)
    ensureAbsoluteIjentNioPath(path2)
    val nioFs = path.nioFs

    return fsBlocking {
      nioFs.ijentFs.sameFile(path.eelPath, path2.eelPath)
    }
      .getOrThrowFileSystemException()
  }

  override fun isHidden(path: Path): Boolean {
    ensureAbsoluteIjentNioPath(path)
    return when (path.nioFs.ijentFs) {
      is IjentFileSystemPosixApi -> path.normalize().fileName.toString().startsWith(".")
      is IjentFileSystemWindowsApi -> TODO("Not implemented for Windows")
    }
  }

  override fun getFileStore(path: Path): FileStore {
    ensureAbsoluteIjentNioPath(path)
    return IjentNioFileStore(path.eelPath, path.nioFs.ijentFs)
  }

  private enum class UnixFilePermissionBranch { OWNER, GROUP, OTHER }

  override fun checkAccess(path: Path, vararg modes: AccessMode) {
    val fs = ensureAbsoluteIjentNioPath(path).nioFs
    fsBlocking {
      when (val ijentFs = fs.ijentFs) {
        is IjentFileSystemPosixApi -> {
          val fileInfo = ijentFs
            // According to the Javadoc, this method must follow symlinks.
            .stat(path.eelPath)
            .resolveAndFollow()
            .getOrThrowFileSystemException()

          if (ijentFs.user.uid == 0) {
            return@fsBlocking
          }

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
    ensureAbsoluteIjentNioPath(path)
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
    val fs = ensureAbsoluteIjentNioPath(path).nioFs

    val linkPolicy = if (LinkOption.NOFOLLOW_LINKS in options) {
      EelFileSystemApi.SymlinkPolicy.DO_NOT_RESOLVE
    }
    else {
      EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW
    }

    val result = when (val ijentFs = fs.ijentFs) {
      is IjentFileSystemPosixApi ->
        IjentNioPosixFileAttributes(fsBlocking {
          ijentFs.stat(path.eelPath).symlinkPolicy(linkPolicy).getOrThrowFileSystemException()
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

  override fun setAttribute(path: Path, attribute: String, value: Any, vararg options: LinkOption) {
    val (viewName, requestedAttributes) = parseAttributesParameter(attribute)
    val nioFs = ensureAbsoluteIjentNioPath(path).nioFs
    val builder = EelFileSystemApi.ChangeAttributesOptions.Builder()
    when (viewName) {
      "basic" -> when (requestedAttributes.singleOrNull()) {
        "lastModifiedTime" -> builder.updateTime(EelFileSystemApi.ChangeAttributesOptions.Builder::modificationTime, value)
        "lastAccessTime" -> builder.updateTime(EelFileSystemApi.ChangeAttributesOptions.Builder::accessTime, value)
        "creationTime" -> value as FileTime // intentionally no-op, like in Java; but we need to throw CCE just in case
        else -> throw IllegalArgumentException("Unrecognized attribute: $attribute")
      }
      "posix" -> {
        val oldPermissions =
          (readAttributes(path, PosixFileAttributes::class.java, *options) as IjentNioPosixFileAttributes).fileInfo.permissions
        builder.permissions(when (requestedAttributes.singleOrNull()) {
                              "permissions" -> {
                                value as Set<*> // ClassCastException is expected
                                @Suppress("UNCHECKED_CAST") val mask = PosixFilePermissionsUtil.toUnixMode(value as Set<PosixFilePermission>)
                                EelPosixFileInfoImpl.Permissions(oldPermissions.owner, oldPermissions.group, mask)
                              }
                              "owner" -> {
                                if (value is EelPosixUserPrincipal) {
                                  if (value.uid != oldPermissions.owner) {
                                    TODO("Changing uid is not supported yet")
                                  }
                                  oldPermissions
                                }
                                else {
                                  throw UnsupportedOperationException("Unsupported owner principal: $value")
                                }
                              }
                              "group" -> {
                                if (value is EelPosixGroupPrincipal) {
                                  if (value.gid != oldPermissions.group) {
                                    TODO("Changing gid is not supported yet")
                                  }
                                  oldPermissions
                                }
                                else {
                                  throw java.lang.UnsupportedOperationException("Unsupported group principal: $value")
                                }
                              }
                              else -> throw IllegalArgumentException("Unrecognized attribute: $attribute")
                            })
      }
      else -> throw java.lang.IllegalArgumentException("Unrecognized attribute: $attribute")
    }
    fsBlocking {
      nioFs.ijentFs.changeAttributes(path.eelPath, builder.build()).getOrThrowFileSystemException()
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

    val fs = ensureAbsoluteIjentNioPath(link).nioFs
    ensureIjentNioPath(target)
    val eelTarget = when (target) {
      is AbsoluteIjentNioPath -> EelFileSystemPosixApi.SymbolicLinkTarget.Absolute(target.eelPath)
      is RelativeIjentNioPath -> EelFileSystemPosixApi.SymbolicLinkTarget.Relative(target.segments)
    }

    require(target.nioFs == fs) {
      "Can't create symlinks between different file systems"
    }

    fsBlocking {
      when (val ijentFs = fs.ijentFs) {
        is IjentFileSystemPosixApi -> ijentFs.createSymbolicLink(eelTarget, link.eelPath).getOrThrowFileSystemException()
        is IjentFileSystemWindowsApi -> TODO("Symbolic links are not supported on Windows")
      }
    }
  }

  override fun createLink(link: Path?, existing: Path?) {
    TODO("Not yet implemented -> com.intellij.platform.ijent.functional.fs.TodoOperation.HARD_LINK")
  }

  override fun readSymbolicLink(link: Path): Path {
    val fs = ensureAbsoluteIjentNioPath(link).nioFs
    val absolutePath = link.eelPath
    val os = fs.ijentFs.descriptor.platform
    return fsBlocking {
      when (val ijentFs = fs.ijentFs) {
        is IjentFileSystemPosixApi -> when (val type = ijentFs.stat(absolutePath).justResolve().getOrThrowFileSystemException().type) {
          is Symlink.Resolved.Absolute -> AbsoluteIjentNioPath(type.result, link.nioFs, null)
          is Symlink.Resolved.Relative -> {
            RelativeIjentNioPath(type.result.split(*os.directorySeparators), link.nioFs)
          }
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
  private fun ensureAbsoluteIjentNioPath(path: Path): AbsoluteIjentNioPath {
    contract {
      returns() implies (path is AbsoluteIjentNioPath)
    }

    if (path !is AbsoluteIjentNioPath) {
      throw ProviderMismatchException("$path (${path.javaClass}) is not ${IjentNioPath::class.java.simpleName}")
    }

    return path
  }
}


internal fun EelFileSystemApi.ChangeAttributesOptions.Builder.updateTime(selector: EelFileSystemApi.ChangeAttributesOptions.Builder.(EelFileSystemApi.TimeSinceEpoch) -> Unit, obj: Any) {
  obj as FileTime // ClassCastException is expected
  val instant = obj.toInstant()
  selector(EelFileSystemApi.timeSinceEpoch(instant.epochSecond.toULong(), instant.nano.toUInt()))
}