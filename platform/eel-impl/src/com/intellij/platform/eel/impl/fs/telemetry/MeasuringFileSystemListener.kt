// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.FileSystemTracingListener
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.time.Instant
import kotlin.io.path.extension

internal class MeasuringFileSystemListener : FileSystemTracingListener {

  init {
    // ensure the class is loaded to avoid recursion
    Measurer.Operation::class to Measurer to TracingSeekableByteChannel::class to TracingDirectoryStream::class
  }

  private class State {
    val currentOperations: ArrayList<SpanEntry> = arrayListOf()
    var recursionFlag: Boolean = false
  }
  private data class SpanEntry(
    val span: Span,
    val operation: Measurer.Operation,
    val delegate: FileSystemProvider,
    val startTime: Instant,
  )

  private val openOperations: ThreadLocal<State> = ThreadLocal.withInitial { State() }

  fun spanNamePrefixWithFileSystemClass(delegate: FileSystemProvider): String =
    Measurer.DelegateType.fromDelegateClass(delegate.javaClass).toString()

  fun opStarted(delegate: FileSystemProvider, operation: Measurer.Operation) {
    if (openOperations.get().recursionFlag) return
    openOperations.get().recursionFlag = true
    val span = Measurer.ijentTracer.spanBuilder("${spanNamePrefixWithFileSystemClass(delegate)}.${operation.name}", TracerLevel.DETAILED).startSpan()
    Measurer.eventsCounter.incrementAndGet()
    openOperations.get().currentOperations.add(SpanEntry(span, operation, delegate, Instant.now()))
    openOperations.get().recursionFlag = false
  }
  fun opFinished(delegate: FileSystemProvider, path1: Path?, path2: Path?, operation: Measurer.Operation, err: IOException?) {
    val currentOperations = openOperations.get().currentOperations
    if (currentOperations.isNotEmpty()) {
      if (currentOperations.last().operation == operation && currentOperations.last().delegate == delegate) {
        val entry = currentOperations.removeLast()
        if (err != null) {
          entry.span.setStatus(StatusCode.ERROR)
        }
        entry.span.end()
        Measurer.reportFsEvent(delegate, path1, path2, operation, entry.startTime, Instant.now(), err == null)
      }
      else {
        LOG.error("bracket sequence invariant is not satisfied")
      }
    }
  }

  companion object {
    private val LOG = logger<MeasuringFileSystemListener>()
  }

  override fun providerCheckAccessStarted(delegate: FileSystemProvider, path: Path?, vararg modes: AccessMode?) =
    opStarted(delegate, Measurer.Operation.providerCheckAccess)
  override fun providerCheckAccessReturn(delegate: FileSystemProvider, path: Path?, vararg modes: AccessMode?) =
    opFinished(delegate, path, null, Measurer.Operation.providerCheckAccess, null)
  override fun providerCheckAccessError(delegate: FileSystemProvider, err: IOException, path: Path?, vararg modes: AccessMode?) =
    opFinished(delegate, path, null, Measurer.Operation.providerCheckAccess, err)

  override fun providerCopyStarted(delegate: FileSystemProvider, source: Path?, target: Path?, vararg options: CopyOption?) =
    opStarted(delegate, Measurer.Operation.providerCopy)
  override fun providerCopyReturn(delegate: FileSystemProvider, source: Path?, target: Path?, vararg options: CopyOption?) =
    opFinished(delegate, source, target, Measurer.Operation.providerCopy, null)
  override fun providerCopyError(delegate: FileSystemProvider, err: IOException, source: Path?, target: Path?, vararg options: CopyOption?) =
    opFinished(delegate, source, target, Measurer.Operation.providerCopy, err)

  override fun providerCreateDirectoryStarted(delegate: FileSystemProvider, dir: Path?, vararg attrs: FileAttribute<*>?) =
    opStarted(delegate, Measurer.Operation.providerCreateDirectory)
  override fun providerCreateDirectoryReturn(delegate: FileSystemProvider, dir: Path?, vararg attrs: FileAttribute<*>?) =
    opFinished(delegate, dir, null, Measurer.Operation.providerCreateDirectory, null)
  override fun providerCreateDirectoryError(delegate: FileSystemProvider, err: IOException, dir: Path?, vararg attrs: FileAttribute<*>?) =
    opFinished(delegate, dir, null, Measurer.Operation.providerCreateDirectory, err)

  override fun providerCreateLinkStarted(delegate: FileSystemProvider, link: Path?, existing: Path?) =
    opStarted(delegate, Measurer.Operation.providerCreateLink)
  override fun providerCreateLinkReturn(delegate: FileSystemProvider, link: Path?, existing: Path?) =
    opFinished(delegate, link, null, Measurer.Operation.providerCreateLink, null)
  override fun providerCreateLinkError(delegate: FileSystemProvider, err: IOException, link: Path?, existing: Path?) =
    opFinished(delegate, link, null, Measurer.Operation.providerCreateLink, err)

  override fun providerCreateSymbolicLinkStarted(delegate: FileSystemProvider, link: Path?, target: Path?, vararg attrs: FileAttribute<*>?) =
    opStarted(delegate, Measurer.Operation.providerCreateSymbolicLink)
  override fun providerCreateSymbolicLinkReturn(delegate: FileSystemProvider, link: Path?, target: Path?, vararg attrs: FileAttribute<*>?) =
    opFinished(delegate, link, target, Measurer.Operation.providerCreateSymbolicLink, null)
  override fun providerCreateSymbolicLinkError(delegate: FileSystemProvider, err: IOException, link: Path?, target: Path?, vararg attrs: FileAttribute<*>?) =
    opFinished(delegate, link, target, Measurer.Operation.providerCreateSymbolicLink, err)

  override fun providerDeleteStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, Measurer.Operation.providerDelete)
  override fun providerDeleteReturn(delegate: FileSystemProvider, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerDelete, null)
  override fun providerDeleteError(delegate: FileSystemProvider, err: IOException, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerDelete, err)

  override fun providerDeleteIfExistsStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, Measurer.Operation.providerDeleteIfExists)
  override fun providerDeleteIfExistsReturn(delegate: FileSystemProvider, result: Boolean, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerDeleteIfExists, null)
  override fun providerDeleteIfExistsError(delegate: FileSystemProvider, err: IOException, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerDeleteIfExists, err)

  override fun providerGetFileAttributeViewStarted(delegate: FileSystemProvider, path: Path?, type: Class<out FileAttributeView>?, vararg options: LinkOption?) =
    opStarted(delegate, Measurer.Operation.providerGetFileAttributeView)
  override fun providerGetFileAttributeViewReturn(delegate: FileSystemProvider, result: FileAttributeView?, path: Path?, type: Class<out FileAttributeView>?, vararg options: LinkOption?) =
    opFinished(delegate, path, null, Measurer.Operation.providerGetFileAttributeView, null)

  override fun providerGetFileStoreStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, Measurer.Operation.providerGetFileStore)
  override fun providerGetFileStoreReturn(delegate: FileSystemProvider, result: FileStore?, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerGetFileStore, null)
  override fun providerGetFileStoreError(delegate: FileSystemProvider, err: IOException, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerGetFileStore, err)

  override fun providerIsHiddenStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, Measurer.Operation.providerIsHidden)
  override fun providerIsHiddenReturn(delegate: FileSystemProvider, result: Boolean, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerIsHidden, null)
  override fun providerIsHiddenError(delegate: FileSystemProvider, err: IOException, path: Path?) =
    opFinished(delegate, path, null, Measurer.Operation.providerIsHidden, err)

  override fun providerIsSameFileStarted(delegate: FileSystemProvider, path: Path?, path2: Path?) =
    opStarted(delegate, Measurer.Operation.providerIsSameFile)
  override fun providerIsSameFileReturn(delegate: FileSystemProvider, result: Boolean, path: Path?, path2: Path?) =
    opFinished(delegate, path, path2, Measurer.Operation.providerIsSameFile, null)
  override fun providerIsSameFileError(delegate: FileSystemProvider, err: IOException, path: Path?, path2: Path?) =
    opFinished(delegate, path, path2, Measurer.Operation.providerIsSameFile, err)

  override fun providerMoveStarted(delegate: FileSystemProvider, source: Path?, target: Path?, vararg options: CopyOption?) =
    opStarted(delegate, Measurer.Operation.providerMove)
  override fun providerMoveReturn(delegate: FileSystemProvider, source: Path?, target: Path?, vararg options: CopyOption?) =
    opFinished(delegate, source, target, Measurer.Operation.providerMove, null)
  override fun providerMoveError(delegate: FileSystemProvider, err: IOException, source: Path?, target: Path?, vararg options: CopyOption?) =
    opFinished(delegate, source, target, Measurer.Operation.providerMove, err)

  override fun providerNewByteChannelStarted(delegate: FileSystemProvider, path: Path?, options: MutableSet<out OpenOption?>?, vararg attrs: FileAttribute<*>?) {
    if (path?.extension == "class") {
      return
    }
    opStarted(delegate, Measurer.Operation.providerNewByteChannel)
  }
  override fun providerNewByteChannelReturn(delegate: FileSystemProvider, result: SeekableByteChannel?, path: Path?, options: MutableSet<out OpenOption?>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel? {
    if (path?.extension == "class") {
      return result
    }
    opFinished(delegate, path, null, Measurer.Operation.providerNewByteChannel, null)
    return result?.let { TracingSeekableByteChannel(it, this.spanNamePrefixWithFileSystemClass(delegate)) }
  }
  override fun providerNewByteChannelError(delegate: FileSystemProvider, err: IOException, path: Path?, options: MutableSet<out OpenOption?>?, vararg attrs: FileAttribute<*>?) =
    opFinished(delegate, path, null, Measurer.Operation.providerNewByteChannel, err)

  override fun providerNewDirectoryStreamStarted(delegate: FileSystemProvider, dir: Path?, filter: DirectoryStream.Filter<in Path?>?) =
    opStarted(delegate, Measurer.Operation.providerNewDirectoryStream)
  override fun providerNewDirectoryStreamReturn(delegate: FileSystemProvider, result: DirectoryStream<Path>?, dir: Path?, filter: DirectoryStream.Filter<in Path?>?): DirectoryStream<Path>? {
    opFinished(delegate, dir, null, Measurer.Operation.providerNewDirectoryStream, null)
    return result?.let { TracingDirectoryStream(it, this.spanNamePrefixWithFileSystemClass(delegate)) }
  }
  override fun providerNewDirectoryStreamError(delegate: FileSystemProvider, err: IOException, dir: Path?, filter: DirectoryStream.Filter<in Path?>?) =
    opFinished(delegate, dir, null, Measurer.Operation.providerNewDirectoryStream, err)

  override fun providerReadAttributesStarted(delegate: FileSystemProvider, path: Path?, type: Class<out BasicFileAttributes>?, vararg options: LinkOption?) =
    opStarted(delegate, Measurer.Operation.providerReadAttributes)
  override fun providerReadAttributesReturn(delegate: FileSystemProvider, result: BasicFileAttributes?, path: Path?, type: Class<out BasicFileAttributes>?, vararg options: LinkOption?) =
    opFinished(delegate, path, null, Measurer.Operation.providerReadAttributes, null)
  override fun providerReadAttributesError(delegate: FileSystemProvider, err: IOException, path: Path?, type: Class<out BasicFileAttributes>?, vararg options: LinkOption?) =
    opFinished(delegate, path, null, Measurer.Operation.providerReadAttributes, err)

  override fun providerReadAttributesStarted(delegate: FileSystemProvider, path: Path?, attributes: String?, vararg options: LinkOption?) =
    opStarted(delegate, Measurer.Operation.providerReadAttributes)
  override fun providerReadAttributesReturn(delegate: FileSystemProvider, result: Map<String, Any?>?, path: Path?, attributes: String?, vararg options: LinkOption?) =
    opFinished(delegate, path, null, Measurer.Operation.providerReadAttributes, null)
  override fun providerReadAttributesError(delegate: FileSystemProvider, err: IOException, path: Path?, attributes: String?, vararg options: LinkOption?) =
    opFinished(delegate, path, null, Measurer.Operation.providerReadAttributes, err)

  override fun providerReadSymbolicLinkStarted(delegate: FileSystemProvider, link: Path?) =
    opStarted(delegate, Measurer.Operation.providerReadSymbolicLink)
  override fun providerReadSymbolicLinkReturn(delegate: FileSystemProvider, result: Path?, link: Path?) =
    opFinished(delegate, link, null, Measurer.Operation.providerReadSymbolicLink, null)
  override fun providerReadSymbolicLinkError(delegate: FileSystemProvider, err: IOException, link: Path?) =
    opFinished(delegate, link, null, Measurer.Operation.providerReadSymbolicLink, err)

  override fun providerSetAttributeStarted(delegate: FileSystemProvider, path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) =
    opStarted(delegate, Measurer.Operation.providerSetAttribute)
  override fun providerSetAttributeReturn(delegate: FileSystemProvider, path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) =
    opFinished(delegate, path, null, Measurer.Operation.providerSetAttribute, null)
  override fun providerSetAttributeError(delegate: FileSystemProvider, err: IOException, path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) =
    opFinished(delegate, path, null, Measurer.Operation.providerSetAttribute, err)

}