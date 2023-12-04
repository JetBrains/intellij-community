// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs.nio

import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.IjentSessionRegistry
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemApi.FileInfo.Type.*
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.*
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@ApiStatus.Experimental
class IjentNioFileSystemProvider : FileSystemProvider() {
  private val registeredFileSystems = ConcurrentHashMap<IjentId, IjentNioFileSystem>()

  override fun getScheme(): String = "ijent"

  override fun newFileSystem(uri: URI, env: MutableMap<String, *>?): FileSystem {
    typicalUriChecks(uri)

    if (!uri.path.isNullOrEmpty()) {
      TODO("Filesystems with non-empty paths are not supported yet.")
    }

    val ijentId = IjentId(uri.host)
    val ijentApi = IjentSessionRegistry.instance().ijents[ijentId]

    require(ijentApi != null) {
      "$ijentApi is not registered in ${IjentSessionRegistry::class.java.simpleName}"
    }

    val fs = IjentNioFileSystem(this, ijentApi.fs)

    if (registeredFileSystems.putIfAbsent(ijentId, fs) != null) {
      throw FileSystemAlreadyExistsException("A filesystem for $ijentId is already registered")
    }

    return fs
  }

  override fun getFileSystem(uri: URI): FileSystem {
    typicalUriChecks(uri)
    return registeredFileSystems[IjentId(uri.host)] ?: throw FileSystemNotFoundException()
  }

  override fun getPath(uri: URI): Path =
    getFileSystem(uri).getPath(uri.path)

  override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>): SeekableByteChannel =
    newFileChannel(path, options, *attrs)

  override fun newFileChannel(path: Path, options: MutableSet<out OpenOption>?, vararg attrs: FileAttribute<*>?): FileChannel {
    TODO()
  }

  override fun newDirectoryStream(dir: Path, pathFilter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path> {
    require(dir is IjentNioPath)

    val nioFs = getFs(dir)

    return nioFs.fsBlocking {
      val childrenNames = nioFs.ijentFsApi.listDirectory(dir.ijentPath).getOrThrow(dir.toString())

      val nioPathList = childrenNames.asSequence()
        .map(dir.ijentPath::resolve)
        .map { ijentRemotePath -> IjentNioPath(ijentRemotePath, nioFs) }
        .filter { nioPath -> pathFilter?.accept(nioPath) == true }
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
    TODO("Not yet implemented")
  }

  override fun isHidden(path: Path): Boolean {
    TODO("Not yet implemented")
  }

  override fun getFileStore(path: Path): FileStore =
    IjentNioFileStore(getFs(path).ijentFsApi)


  override fun checkAccess(path: Path, vararg modes: AccessMode) {
    if (modes.isNotEmpty()) {
      TODO("Not yet implemented")
    }

    val fs = getFs(path)
    fs.fsBlocking {
      // According to the javadoc, this method must follow symlinks.
      fs.ijentFsApi.stat(path.ijentPath, resolveSymlinks = true).getOrThrow(path.toString())
    }
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path, type: Class<V>?, vararg options: LinkOption?): V {
    TODO("Not yet implemented")
  }

  override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A {
    val fs = getFs(path)
    val fileInfo: IjentFileSystemApi.FileInfo = fs.fsBlocking {
      @Suppress("NAME_SHADOWING") var path = path.ijentPath
      while (true) {
        val fi = fs.ijentFsApi
          .stat(path, resolveSymlinks = LinkOption.NOFOLLOW_LINKS in options)
          .getOrThrow(path.toString())
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
  private fun getFs(path: Path): IjentNioFileSystem {
    contract {
      returns() implies (path is IjentNioPath)
    }

    require(path is IjentNioPath)
    val ijentId = path.ijentPath.ijentId
    val nioFs = registeredFileSystems[ijentId]
    check(nioFs != null) { "No filesystem registered for $ijentId" }
    return nioFs
  }

  private fun typicalUriChecks(uri: URI) {
    require(uri.host.isNotEmpty())

    require(uri.scheme == scheme)
    require(uri.userInfo.isNullOrEmpty())
    require(uri.query.isNullOrEmpty())
    require(uri.fragment.isNullOrEmpty())
  }
}