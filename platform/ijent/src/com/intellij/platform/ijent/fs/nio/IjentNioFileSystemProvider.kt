// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs.nio

import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.IjentSessionRegistry
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemApi.FileInfo.Type.*
import com.intellij.platform.ijent.fs.IjentFileSystemApi.SameFile
import com.intellij.platform.ijent.fs.IjentFileSystemApi.Stat
import com.intellij.platform.ijent.fs.IjentFsResult
import com.intellij.platform.ijent.fs.IjentPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.*
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@ApiStatus.Experimental
class IjentNioFileSystemProvider : FileSystemProvider() {
  companion object {
    private var ijentFsApiGetter: (IjentId) -> IjentFileSystemApi = { ijentId ->
      val ijentApi = IjentSessionRegistry.instance().ijents[ijentId]

      require(ijentApi != null) {
        "$ijentApi is not registered in ${IjentSessionRegistry::class.java.simpleName}"
      }

      ijentApi.fs
    }

    @TestOnly
    fun mockIjentFsApiGetter(scope: CoroutineScope, getter: (IjentId) -> IjentFileSystemApi) {
      val oldGetter = ijentFsApiGetter
      scope.coroutineContext.job.invokeOnCompletion {
        ijentFsApiGetter = oldGetter
      }
      ijentFsApiGetter = getter
    }

    @JvmStatic
    fun getInstance(): IjentNioFileSystemProvider =
      installedProviders()
        .filterIsInstance<IjentNioFileSystemProvider>()
        .single()
  }

  private val registeredFileSystems = ConcurrentHashMap<IjentId, IjentNioFileSystem>()

  override fun getScheme(): String = "ijent"

  override fun newFileSystem(uri: URI, env: MutableMap<String, *>?): IjentNioFileSystem {
    typicalUriChecks(uri)

    if (!uri.path.isNullOrEmpty()) {
      TODO("Filesystems with non-empty paths are not supported yet.")
    }

    val ijentId = IjentId(uri.host)
    val ijentApiFs = ijentFsApiGetter(ijentId)
    val fs = IjentNioFileSystem(this, ijentApiFs)

    if (registeredFileSystems.putIfAbsent(ijentId, fs) != null) {
      throw FileSystemAlreadyExistsException("A filesystem for $ijentId is already registered")
    }

    ijentApiFs.coroutineScope.coroutineContext.job.invokeOnCompletion {
      registeredFileSystems.remove(ijentId)
    }

    return fs
  }

  override fun getFileSystem(uri: URI): IjentNioFileSystem {
    typicalUriChecks(uri)
    return registeredFileSystems[IjentId(uri.host)] ?: throw FileSystemNotFoundException()
  }

  override fun getPath(uri: URI): IjentNioPath =
    getFileSystem(uri).run {
      getPath(
        if (isWindows) uri.path.trimStart('/')
        else uri.path
      )
    }

  override fun newByteChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>): SeekableByteChannel =
    newFileChannel(path, options, *attrs)

  override fun newFileChannel(path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>?): FileChannel {
    ensureIjentNioPath(path)
    require(path.ijentPath is IjentPath.Absolute)
    // TODO Handle options and attrs
    val fs = registeredFileSystems[path.ijentId] ?: throw FileSystemNotFoundException()

    require(!(READ in options && WRITE in options)) { "READ + WRITE not allowed" }
    require(!(READ in options && APPEND in options)) { "READ + APPEND not allowed" }
    require(!(APPEND in options && TRUNCATE_EXISTING in options)) { "APPEND + TRUNCATE_EXISTING not allowed" }

    return if (WRITE in options || APPEND in options) {
      if (DELETE_ON_CLOSE in options) TODO("WRITE + CREATE_NEW")
      if (LinkOption.NOFOLLOW_LINKS in options) TODO("WRITE + NOFOLLOW_LINKS")

      fs.fsBlocking {
        IjentNioFileChannel.createWriting(
          fs,
          path.ijentPath,
          append = APPEND in options,
          creationMode = when {
            CREATE_NEW in options -> IjentFileSystemApi.FileWriterCreationMode.ONLY_CREATE
            CREATE in options -> IjentFileSystemApi.FileWriterCreationMode.ALLOW_CREATE
            else -> IjentFileSystemApi.FileWriterCreationMode.ONLY_OPEN_EXISTING
          },
        )
      }
    }
    else {
      if (CREATE in options) TODO("READ + CREATE")
      if (CREATE_NEW in options) TODO("READ + CREATE_NEW")
      if (DELETE_ON_CLOSE in options) TODO("READ + CREATE_NEW")
      if (LinkOption.NOFOLLOW_LINKS in options) TODO("READ + NOFOLLOW_LINKS")

      fs.fsBlocking {
        IjentNioFileChannel.createReading(fs, path.ijentPath)
      }
    }
  }

  override fun newDirectoryStream(dir: Path, pathFilter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> {
    ensureIjentNioPath(dir)
    val nioFs = dir.nioFs

    return nioFs.fsBlocking {
      val childrenNames = when (val v = nioFs.ijentFsApi.listDirectory(ensurePathIsAbsolute(dir.ijentPath))) {
        is IjentFileSystemApi.ListDirectory.Ok -> v.value
        is IjentFsResult.Error -> v.throwFileSystemException()
      }

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
    TODO("Not yet implemented")
  }

  override fun delete(path: Path) {
    TODO("Not yet implemented")
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    TODO("Not yet implemented")
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    TODO("Not yet implemented")
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    ensureIjentNioPath(path)
    ensureIjentNioPath(path2)
    val nioFs = path.nioFs

    return nioFs.fsBlocking {
      when (val v = nioFs.ijentFsApi.sameFile(ensurePathIsAbsolute(path.ijentPath), ensurePathIsAbsolute(path2.ijentPath))) {
        is SameFile.Ok -> v.value
        is IjentFsResult.Error -> v.throwFileSystemException()
      }
    }
  }

  override fun isHidden(path: Path): Boolean {
    TODO("Not yet implemented")
  }

  override fun getFileStore(path: Path): FileStore =
    IjentNioFileStore(ensureIjentNioPath(path).nioFs.ijentFsApi)


  override fun checkAccess(path: Path, vararg modes: AccessMode) {
    if (modes.isNotEmpty()) {
      TODO("Not yet implemented")
    }

    val fs = ensureIjentNioPath(path).nioFs
    fs.fsBlocking {
      // According to the javadoc, this method must follow symlinks.
      when (val v = fs.ijentFsApi.stat(ensurePathIsAbsolute(path.ijentPath), resolveSymlinks = true)) {
        is Stat.Ok -> v.value
        is IjentFsResult.Error -> v.throwFileSystemException()
      }
    }
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path, type: Class<V>?, vararg options: LinkOption?): V {
    TODO("Not yet implemented")
  }

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    val fs = ensureIjentNioPath(path).nioFs
    val fileInfo: IjentFileSystemApi.FileInfo = fs.fsBlocking {
      @Suppress("NAME_SHADOWING") var path: IjentPath.Absolute = ensurePathIsAbsolute(path.ijentPath)
      while (true) {
        val fi = when (val v = fs.ijentFsApi.stat(path, resolveSymlinks = LinkOption.NOFOLLOW_LINKS in options)) {
          is Stat.Ok -> v.value
          is IjentFsResult.Error -> v.throwFileSystemException()
        }
        when (val t = fi.fileType) {
          Directory, Other, Regular, Symlink.Unresolved -> return@fsBlocking fi

          is Symlink.Resolved -> {
            path = t.result
          }
        }
      }
      error("Can never reach here")
    }

    val basic = object : BasicFileAttributes {
      override fun lastModifiedTime(): FileTime {
        TODO("Not yet implemented")
      }

      override fun lastAccessTime(): FileTime {
        TODO("Not yet implemented")
      }

      override fun creationTime(): FileTime {
        TODO("Not yet implemented")
      }

      override fun isRegularFile(): Boolean =
        when (fileInfo.fileType) {
          Regular -> true
          Directory, Other, is Symlink -> false
        }

      override fun isDirectory(): Boolean =
        when (fileInfo.fileType) {
          Directory -> true
          Other, Regular, is Symlink -> false
        }

      override fun isSymbolicLink(): Boolean =
        when (fileInfo.fileType) {
          is Symlink -> true
          Directory, Other, Regular -> false
        }

      override fun isOther(): Boolean =
        when (fileInfo.fileType) {
          Other -> true
          Directory, Regular, is Symlink -> false
        }

      override fun size(): Long {
        TODO("Not yet implemented")
      }

      override fun fileKey(): Any {
        TODO("Not yet implemented")
      }
    }

    val result = when (type) {
      BasicFileAttributes::class.java -> object : DosFileAttributes, BasicFileAttributes by basic {
        override fun isReadOnly(): Boolean {
          TODO("Not yet implemented")
        }

        override fun isHidden(): Boolean {
          TODO("Not yet implemented")
        }

        override fun isArchive(): Boolean {
          TODO("Not yet implemented")
        }

        override fun isSystem(): Boolean {
          TODO("Not yet implemented")
        }
      }

      DosFileAttributes::class.java -> TODO()

      PosixFileAttributes::class.java -> object : PosixFileAttributes, BasicFileAttributes by basic {
        override fun owner(): UserPrincipal {
          TODO("Not yet implemented")
        }

        override fun group(): GroupPrincipal {
          TODO("Not yet implemented")
        }

        override fun permissions(): Set<PosixFilePermission> {
          TODO("Not yet implemented")
        }
      }

      else -> throw NotImplementedError()
    }

    @Suppress("UNCHECKED_CAST")
    return result as A
  }

  override fun readAttributes(path: Path, attributes: String?, vararg options: LinkOption): MutableMap<String, Any> {
    TODO("Not yet implemented")
  }

  override fun setAttribute(path: Path, attribute: String?, value: Any?, vararg options: LinkOption?) {
    TODO("Not yet implemented")
  }

  @OptIn(ExperimentalContracts::class)
  private fun ensureIjentNioPath(path: Path): IjentNioPath {
    contract {
      returns() implies (path is IjentNioPath)
    }

    if (path !is IjentNioPath) {
      throw ProviderMismatchException("$path is not ${IjentNioPath::class.java.simpleName}")
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
    require(uri.host.isNotEmpty())

    require(uri.scheme == scheme)
    require(uri.userInfo.isNullOrEmpty())
    require(uri.query.isNullOrEmpty())
    require(uri.fragment.isNullOrEmpty())
  }
}