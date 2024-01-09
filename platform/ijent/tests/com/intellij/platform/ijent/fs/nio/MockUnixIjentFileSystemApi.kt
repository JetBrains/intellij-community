// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs.nio

import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.fs.*
import com.intellij.platform.ijent.fs.IjentFileSystemApi.FileWriterCreationMode.*
import com.intellij.platform.ijent.fs.IjentFileSystemApi.ListDirectory
import com.intellij.platform.ijent.fs.IjentFileSystemApi.ListDirectoryWithAttrs
import com.intellij.platform.ijent.fs.impl.IjentFsResultImpl
import com.intellij.platform.ijent.fs.nio.MockIjentFileSystemApi.MockResult.Err
import com.intellij.platform.ijent.fs.nio.MockIjentFileSystemApi.MockResult.Ok
import kotlinx.coroutines.CoroutineScope
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.reflect.KMutableProperty0

class MockIjentFileSystemApi(
  override val coroutineScope: CoroutineScope,
  override val id: IjentId,
  override val isWindows: Boolean,
  val fileTree: ImaginaryFileTree = ImaginaryFileTree(),
) : IjentFileSystemApi {
  private fun <P : IjentPath> IjentPathResult<P>.getOrThrow(): P =
    when (this) {
      is IjentPathResult.Ok -> path
      is IjentPathResult.Err -> error(toString())
    }

  override suspend fun getRootDirectories(): Collection<IjentPath.Absolute> =
    fileTree.roots.map { dir ->
      IjentPath.Absolute.build(dir.name).getOrThrow()
    }

  private var userHome: IjentPath.Absolute? = null

  override suspend fun userHome(): IjentPath.Absolute? = userHome

  override suspend fun listDirectory(path: IjentPath.Absolute): ListDirectory =
    when (val directoryChildren = getDirectoryChildren(path)) {
      is Ok -> IjentFsResultImpl.ListDirectory.Ok(directoryChildren.result.map { node -> node.name })
      is Err -> directoryChildren.error as ListDirectory
    }

  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): ListDirectoryWithAttrs =
    when (val directoryChildren = getDirectoryChildren(path)) {
      is Ok -> IjentFsResultImpl.ListDirectoryWithAttrs.Ok(directoryChildren.result.map { node ->
        IjentFileSystemApi.FileInfo(
          path = path.getChild(node.name).getOrThrow(),
          fileType = when (node) {
            is ImaginaryFileTree.Node.Directory -> IjentFileSystemApi.FileInfo.Type.Directory
            is ImaginaryFileTree.Node.RegularFile -> IjentFileSystemApi.FileInfo.Type.Regular
          }
        )
      })
      is Err -> directoryChildren.error as ListDirectoryWithAttrs
    }

  private fun getDirectoryChildren(
    path: IjentPath.Absolute,
  ): MockResult<Iterable<ImaginaryFileTree.Node<*>>> =
    when (val node = getNode(path)) {
      is Err -> node.typeCasted()
      is Ok -> when (val n = node.result) {
        is ImaginaryFileTree.Node.Directory -> Ok(n.children)
        is ImaginaryFileTree.Node.RegularFile -> Err(IjentFsResultImpl.NotDirectory(path, ""))
      }
    }

  private fun getNode(path: IjentPath.Absolute): MockResult<ImaginaryFileTree.Node<*>> {
    val result = generateSequence(path.normalize().getOrThrow(), IjentPath.Absolute::parent)
      .toList()
      .reversed()
      .fold(null as ImaginaryFileTree.Node<*>?) { parent, childPath ->
        val children = when (parent) {
          null -> fileTree.roots
          is ImaginaryFileTree.Node.Directory -> parent.children
          is ImaginaryFileTree.Node.RegularFile -> {
            return Err(IjentFsResultImpl.NotDirectory(childPath, "This is ${parent.javaClass.simpleName}"))
          }
        }

        children
          .find { it.name == childPath.fileName }
        ?: return Err(IjentFsResultImpl.DoesNotExist(childPath, ""))
      }

    // TODO Suppose that there's a regular file /a/b. What errno will return stat(/a/b/c/d)?

    return Ok(result!!)
  }

  private sealed interface MockResult<T> {
    data class Ok<T>(val result: T) : MockResult<T>
    data class Err<T>(val error: IjentFsResult.Error) : MockResult<T>
  }

  @Suppress("UNCHECKED_CAST")
  private fun <A, B> Err<A>.typeCasted(): Err<B> =
    this as Err<B>

  override suspend fun canonicalize(path: IjentPath.Absolute): IjentFileSystemApi.Canonicalize {
    TODO("Not yet implemented")
  }

  override suspend fun stat(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFileSystemApi.Stat {
    TODO("Not yet implemented")
  }

  override suspend fun sameFile(
    source: IjentPath.Absolute,
    target: IjentPath.Absolute,
  ): IjentFileSystemApi.SameFile =
    when (val sourceNode = getNode(source)) {
      is Err -> sourceNode.error as IjentFileSystemApi.SameFile
      is Ok -> when (val targetNode = getNode(target)) {
        is Err -> targetNode.error as IjentFileSystemApi.SameFile
        is Ok -> IjentFsResultImpl.SameFile.Ok(source.normalize() == target.normalize())
      }
    }

  override suspend fun fileReader(path: IjentPath.Absolute): IjentFileSystemApi.FileReader =
    when (val maybeNode = getNode(path)) {
      is Ok ->
        IjentFsResultImpl.FileReader.Ok(when (val node = maybeNode.result) {
          is ImaginaryFileTree.Node.Directory ->
            AlwaysFailingMockNodeIjentOpenedFile(path) { IjentFsResultImpl.NotFile(path, "") }

          is ImaginaryFileTree.Node.RegularFile ->
            MockNodeIjentOpenedFile(path, node::contents)
        })

      is Err ->
        maybeNode.error as IjentFileSystemApi.FileReader  // TODO Can these type casts be avoided?
    }

  override suspend fun fileWriter(
    path: IjentPath.Absolute,
    append: Boolean,
    creationMode: IjentFileSystemApi.FileWriterCreationMode,
  ): IjentFileSystemApi.FileWriter =
    when (val maybeNode = getNode(path)) {
      is Ok ->
        when (val node = maybeNode.result) {
          is ImaginaryFileTree.Node.Directory ->
            IjentFsResultImpl.NotFile(path, "")

          is ImaginaryFileTree.Node.RegularFile -> {
            val openedFile = MockNodeIjentOpenedFile(path, node::contents)
            if (append) {
              openedFile.seek(0, IjentOpenedFile.SeekWhence.END)
            }
            else {
              node.contents = byteArrayOf()
            }
            IjentFsResultImpl.FileWriter.Ok(openedFile)
          }
        }

      is Err ->
        when (creationMode) {
          ALLOW_CREATE, ONLY_CREATE -> {
            if (maybeNode.error.where == path) {
              when (maybeNode.error) {
                is IjentFsResult.DoesNotExist ->
                  when (val maybeParentNode = path.parent?.let(::getNode)) {
                    null ->
                      maybeNode.error

                    is Err ->
                      maybeParentNode.error as IjentFileSystemApi.FileWriter

                    is Ok ->
                      when (val maybeDirectory = maybeParentNode.result) {
                        is ImaginaryFileTree.Node.RegularFile ->
                          IjentFsResultImpl.NotFile(path, "")

                        is ImaginaryFileTree.Node.Directory -> {
                          val file = ImaginaryFileTree.Node.RegularFile(path.fileName, byteArrayOf())
                          maybeDirectory.children += file
                          IjentFsResultImpl.FileWriter.Ok(MockNodeIjentOpenedFile(path, file::contents))
                        }
                      }
                  }

                else -> maybeNode.error as IjentFileSystemApi.FileWriter // TODO Can these type casts be avoided?
              }
            }
            else
              maybeNode.error as IjentFileSystemApi.FileWriter
          }

          ONLY_OPEN_EXISTING ->
            maybeNode.error as IjentFileSystemApi.FileWriter
        }
    }
}

class MockNodeIjentOpenedFile(
  override val path: IjentPath.Absolute,
  private val dataHolder: KMutableProperty0<ByteArray>,
) : IjentOpenedFile.Reader, IjentOpenedFile.Writer {
  private var pos = 0

  override suspend fun read(buf: ByteBuffer): IjentOpenedFile.Reader.Read {
    val data = dataHolder()
    if (pos >= data.size) {
      return IjentFsResultImpl.Reader.Read.Ok(-1)
    }

    val size = min(data.size - pos, buf.remaining())
    buf.put(data, 0, size)
    pos += size
    return IjentFsResultImpl.Reader.Read.Ok(size)
  }

  override suspend fun write(buf: ByteBuffer): IjentOpenedFile.Writer.Write {
    var data = dataHolder()
    val bufSize = buf.remaining()
    if (pos + bufSize > data.size) {
      data = data.copyOf(pos + bufSize)
      dataHolder.set(data)
    }
    buf.get(data, pos, bufSize)
    pos += bufSize
    return IjentFsResultImpl.Writer.Write.Ok(bufSize)
  }

  override suspend fun truncate() {
    dataHolder.set(dataHolder().copyOf(pos))
  }

  override suspend fun close(): Unit = Unit

  override fun tell(): Long =
    pos.toLong()

  override suspend fun seek(offset: Long, whence: IjentOpenedFile.SeekWhence): IjentOpenedFile.Seek {
    TODO("Not yet implemented")
  }
}

private class AlwaysFailingMockNodeIjentOpenedFile(
  override val path: IjentPath.Absolute,
  private val errorGenerator: () -> IjentFsResult.Error,
) : IjentOpenedFile.Reader, IjentOpenedFile.Writer {
  override suspend fun read(buf: ByteBuffer): IjentOpenedFile.Reader.Read =
    errorGenerator() as IjentOpenedFile.Reader.Read

  override suspend fun write(buf: ByteBuffer): IjentOpenedFile.Writer.Write =
    errorGenerator() as IjentOpenedFile.Writer.Write

  override suspend fun truncate() =
    throw IjentOpenedFile.Writer.TruncateException(errorGenerator() as IjentOpenedFile.Writer.TruncateException.TruncateError)

  override suspend fun close(): Unit = Unit

  override fun tell(): Long {
    return 0
  }

  override suspend fun seek(offset: Long, whence: IjentOpenedFile.SeekWhence): IjentOpenedFile.Seek {
    TODO("Not yet implemented")
  }
}