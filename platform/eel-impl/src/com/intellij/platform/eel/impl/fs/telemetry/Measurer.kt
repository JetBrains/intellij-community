// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.platform.diagnostic.telemetry.helpers.use
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
object Measurer {

  internal val eventsCounter: AtomicLong = AtomicLong()

  @Suppress("EnumEntryName")
  enum class DelegateType() {
    system,
    wsl,
    docker;

    companion object {
      private val classToTypeCache = AtomicReference(mutableMapOf<Class<*>, DelegateType>())
      fun fromDelegateClass(clazz: Class<FileSystemProvider>): DelegateType {
        classToTypeCache.get()[clazz]?.let { return it }
        return classToTypeCache.updateAndGet {
          it.putIfAbsent(clazz, fromDelegateClassInternal(clazz))
          it
        }[clazz]!!
      }
      private fun fromDelegateClassInternal(clazz: Class<FileSystemProvider>): DelegateType {
        if (clazz.name == "com.intellij.platform.ide.impl.wsl.ijent.nio.IjentWslNioFileSystemProvider") return wsl
        if (clazz.name == "com.intellij.docker.ijent.MountsAwareFileSystemProvider") return docker
        if (!RoutingAwareFileSystemProvider::class.java.isAssignableFrom(clazz)) return system
        error("Unknown delegate class: ${clazz.name}")
      }
    }
  }

  data class FsEventKey(
    val delegateType: DelegateType,
    val operation: Operation,
    val success: Boolean,
    val repeated: Boolean?,
  ) {
    companion object {
      val VALUES: List<FsEventKey> = DelegateType.entries.flatMap { delegateType ->
        Operation.entries.flatMap { operation ->
          listOf(true, false).flatMap { success ->
            listOf(null, true, false).map { repeated ->
              FsEventKey(delegateType, operation, success, repeated)
            }
          }
        }
      }
    }
  }

  internal val extendedFsEventsCounter: Map<FsEventKey, AtomicLong> = FsEventKey.VALUES.associateWith { AtomicLong() }
  internal val extendedFsEventsDurationNanos: Map<FsEventKey, AtomicLong> = FsEventKey.VALUES.associateWith { AtomicLong() }
  internal val extendedFsEventsRepeatIntervalNanos: Map<FsEventKey, AtomicLong> = FsEventKey.VALUES.associateWith { AtomicLong() }

  internal val ijentMetricsScope = Scope("ijent", PlatformMetrics, verbose = true)
  internal val ijentTracer by lazy { TelemetryManager.getTracer(ijentMetricsScope) }
  internal val ijentMeter by lazy { TelemetryManager.getMeter(ijentMetricsScope) }

  init {
    ijentMeter.counterBuilder("ijent.events.count").buildObserver().also {
      ijentMeter.batchCallback(
        { it.record(eventsCounter.get()) },
        it
      )
    }
    for (key in FsEventKey.VALUES) {
      val successKey = if (key.success) "success" else "failure"
      val repeatedKey = key.repeated?.let { if (it) "repeated" else "initial" }
      ijentMeter.counterBuilder("ijent.fs.events.${key.delegateType}.${key.operation}.$successKey.$repeatedKey.count").buildObserver().also {
        ijentMeter.batchCallback({ it.record(extendedFsEventsCounter[key]!!.get()) }, it)
      }
      ijentMeter.counterBuilder("ijent.fs.events.${key.delegateType}.${key.operation}.$successKey.$repeatedKey.duration.nanos").buildObserver().also {
        ijentMeter.batchCallback({ it.record(extendedFsEventsDurationNanos[key]!!.get() / 1000) }, it)
      }
      if (key.repeated == true) {
        ijentMeter.counterBuilder("ijent.fs.events.${key.delegateType}.${key.operation}.$successKey.repeat.nanos").buildObserver().also {
          ijentMeter.batchCallback({ it.record(extendedFsEventsRepeatIntervalNanos[key]!!.get() / 1000) }, it)
        }
      }
    }
  }

  @Suppress("EnumEntryName")
  enum class Operation {
    directoryStreamClose,
    directoryStreamIteratorNext,
    directoryStreamIteratorRemove,
    fileSystemClose,
    fileSystemNewWatchService,
    providerCheckAccess,
    providerCopy,
    providerCreateDirectory,
    providerCreateLink,
    providerCreateSymbolicLink,
    providerDelete,
    providerDeleteIfExists,
    providerGetFileAttributeView,
    providerGetFileStore,
    providerIsHidden,
    providerIsSameFile,
    providerMove,
    providerNewByteChannel,
    providerNewDirectoryStream,
    providerReadAttributes,
    providerReadSymbolicLink,
    providerSetAttribute,
    seekableByteChannelClose,
    seekableByteChannelNewPosition,
    seekableByteChannelPosition,
    seekableByteChannelRead,
    seekableByteChannelSize,
    seekableByteChannelTruncate,
    seekableByteChannelWrite,
    supportedFileAttributeViews;
  }

  val fsQueryStatCounter: FsQueryStatCounter? = if (System.getProperty("nio.mrfs.telemetry.count.unique.paths", "false").toBoolean()) {
    FsQueryStatCounter()
  }
  else null

  fun reportFsEvent(delegate: FileSystemProvider, path1: Path?, path2: Path?, operation: Operation, startTime: Instant, endTime: Instant, success: Boolean) {
    val delegateType = DelegateType.fromDelegateClass(delegate.javaClass)
    val (repeated, repeatInterval) = if (fsQueryStatCounter != null) {
      val repeatInterval = fsQueryStatCounter.repeatedTime(delegateType, path1, path2, operation, startTime, endTime, success)
      (repeatInterval != null) to repeatInterval
    }
    else null to null
    val key = FsEventKey(
      delegateType = delegateType,
      operation = operation,
      success = success,
      repeated = repeated
    )
    extendedFsEventsCounter[key]!!.incrementAndGet()
    extendedFsEventsDurationNanos[key]!!.addAndGet(Duration.between(startTime, endTime).toNanos())
    if (repeated != null) {
      extendedFsEventsCounter[key.copy(repeated = null)]!!.incrementAndGet()
      extendedFsEventsDurationNanos[key.copy(repeated = null)]!!.addAndGet(Duration.between(startTime, endTime).toNanos())
    }
    if (repeated == true) {
      extendedFsEventsRepeatIntervalNanos[key]!!.addAndGet(repeatInterval!!.toNanos())
    }
  }

}

internal inline fun <T> Measurer.measure(operation: Measurer.Operation, spanNamePrefix: String = "", body: () -> T): T {
  return ijentTracer.spanBuilder("ijent.${spanNamePrefix.takeIf { it.isEmpty() }?.plus(".")}${operation.name}", TracerLevel.DETAILED).use {
    eventsCounter.incrementAndGet()
    body()
  }
}