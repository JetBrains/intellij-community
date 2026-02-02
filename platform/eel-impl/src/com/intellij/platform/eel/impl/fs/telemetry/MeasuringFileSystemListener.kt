// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.FileSystemTracingListener
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.time.Instant
import kotlin.io.path.extension

internal class MeasuringFileSystemListener : FileSystemTracingListener<MeasuringFileSystemListener.SpanEntry?> {

  init {
    // ensure the class is loaded to avoid recursion
    Measurer.Operation::class to Measurer to TracingSeekableByteChannel::class to TracingDirectoryStream::class
  }

  private class State {
    var recursionFlag: Boolean = false
  }

  data class SpanEntry(
    val span: Span,
    val operation: Measurer.Operation,
    val delegate: FileSystemProvider,
    val path1: Path?,
    val path2: Path?,
    val startTime: Instant,
  )

  private val openOperations: ThreadLocal<State> = ThreadLocal.withInitial { State() }

  fun spanNamePrefixWithFileSystemClass(delegate: FileSystemProvider): String =
    Measurer.DelegateType.fromDelegateClass(delegate.javaClass).toString()

  fun opStarted(delegate: FileSystemProvider, path1: Path?, path2: Path?, operation: Measurer.Operation): SpanEntry? {
    if (openOperations.get().recursionFlag) return null
    openOperations.get().recursionFlag = true
    val span = Measurer.ijentTracer.spanBuilder("${spanNamePrefixWithFileSystemClass(delegate)}.${operation.name}", TracerLevel.DETAILED).startSpan()
    Measurer.eventsCounter.incrementAndGet()
    val spanEntry = SpanEntry(span, operation, delegate, path1, path2, Instant.now())
    openOperations.get().recursionFlag = false
    return spanEntry
  }

  fun opFinished(spanEntry: SpanEntry, err: Throwable?) {
    var unexpectedException = false
    if (err != null) {
      spanEntry.span.setStatus(StatusCode.ERROR)
      if (err !is IOException) {
        LOG.debug("nio method threw unexpected exception", err)
        unexpectedException = true
      }
    }
    spanEntry.span.end()
    val endTime = Instant.now()
    Measurer.reportFsEvent(
      delegate = spanEntry.delegate,
      path1 = spanEntry.path1,
      path2 = spanEntry.path2,
      operation = spanEntry.operation,
      startTime = spanEntry.startTime,
      endTime = endTime,
      success = err == null,
    )
  }

  companion object {
    private val LOG = logger<MeasuringFileSystemListener>()
  }

  override fun providerCheckAccessStarted(delegate: FileSystemProvider, path: Path?, vararg modes: AccessMode?): SpanEntry? =
    opStarted(delegate, path, null, Measurer.Operation.providerCheckAccess)

  override fun providerCopyStarted(delegate: FileSystemProvider, source: Path?, target: Path?, vararg options: CopyOption?) =
    opStarted(delegate, source, target, Measurer.Operation.providerCopy)

  override fun providerCreateDirectoryStarted(delegate: FileSystemProvider, dir: Path?, vararg attrs: FileAttribute<*>?) =
    opStarted(delegate, dir, null, Measurer.Operation.providerCreateDirectory)

  override fun providerCreateLinkStarted(delegate: FileSystemProvider, link: Path?, existing: Path?) =
    opStarted(delegate, link, existing, Measurer.Operation.providerCreateLink)

  override fun providerCreateSymbolicLinkStarted(delegate: FileSystemProvider, link: Path?, target: Path?, vararg attrs: FileAttribute<*>?) =
    opStarted(delegate, link, target, Measurer.Operation.providerCreateSymbolicLink)

  override fun providerDeleteStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, path, null, Measurer.Operation.providerDelete)

  override fun providerDeleteIfExistsStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, path, null, Measurer.Operation.providerDeleteIfExists)

  override fun providerGetFileAttributeViewStarted(delegate: FileSystemProvider, path: Path?, type: Class<out FileAttributeView>?, vararg options: LinkOption?) =
    opStarted(delegate, path, null, Measurer.Operation.providerGetFileAttributeView)

  override fun providerGetFileStoreStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, path, null, Measurer.Operation.providerGetFileStore)

  override fun providerIsHiddenStarted(delegate: FileSystemProvider, path: Path?) =
    opStarted(delegate, path, null, Measurer.Operation.providerIsHidden)

  override fun providerIsSameFileStarted(delegate: FileSystemProvider, path: Path?, path2: Path?) =
    opStarted(delegate, path, path2, Measurer.Operation.providerIsSameFile)

  override fun providerMoveStarted(delegate: FileSystemProvider, source: Path?, target: Path?, vararg options: CopyOption?) =
    opStarted(delegate, source, target, Measurer.Operation.providerMove)

  override fun providerNewByteChannelStarted(delegate: FileSystemProvider, path: Path?, options: MutableSet<out OpenOption?>?, vararg attrs: FileAttribute<*>?): SpanEntry? {
    if (path?.extension == "class") {
      return null
    }
    return opStarted(delegate, path, null, Measurer.Operation.providerNewByteChannel)
  }

  override fun providerNewByteChannelReturn(token: SpanEntry?, result: SeekableByteChannel?): SeekableByteChannel? {
    if (token != null) {
      opFinished(token, null)
      return result?.let { TracingSeekableByteChannel(it, this.spanNamePrefixWithFileSystemClass(token.delegate)) }
    }
    else {
      return result
    }
  }

  override fun providerNewDirectoryStreamStarted(delegate: FileSystemProvider, dir: Path?, filter: DirectoryStream.Filter<in Path?>?) =
    opStarted(delegate, dir, null, Measurer.Operation.providerNewDirectoryStream)

  override fun providerNewDirectoryStreamReturn(token: SpanEntry?, result: DirectoryStream<Path>?): DirectoryStream<Path>? {
    if (token != null) {
      opFinished(token, null)
      return result?.let { TracingDirectoryStream(it, this.spanNamePrefixWithFileSystemClass(token.delegate)) }
    }
    else {
      return result
    }
  }

  override fun providerReadAttributesStarted(delegate: FileSystemProvider, path: Path?, type: Class<out BasicFileAttributes>?, vararg options: LinkOption?) =
    opStarted(delegate, path, null, Measurer.Operation.providerReadAttributes)

  override fun providerReadAttributesStarted(delegate: FileSystemProvider, path: Path?, attributes: String?, vararg options: LinkOption?) =
    opStarted(delegate, path, null, Measurer.Operation.providerReadAttributes)

  override fun providerReadSymbolicLinkStarted(delegate: FileSystemProvider, link: Path?) =
    opStarted(delegate, link, null, Measurer.Operation.providerReadSymbolicLink)

  override fun providerSetAttributeStarted(delegate: FileSystemProvider, path: Path?, attribute: String?, value: Any?, vararg options: LinkOption?) =
    opStarted(delegate, path, null, Measurer.Operation.providerSetAttribute)

  override fun providerGenericReturn(token: SpanEntry?) {
    if (token != null) {
      opFinished(token, null)
    }
  }

  override fun providerGenericError(token: SpanEntry?, err: Throwable?) {
    if (token != null) {
      opFinished(token, err)
    }
  }
}