// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import com.intellij.openapi.util.SystemInfo
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
import kotlin.time.Duration.Companion.nanoseconds

@ApiStatus.Internal
object Measurer {

  internal val ijentMetricsScope = Scope("ijent", PlatformMetrics, verbose = true)
  internal val ijentTracer by lazy { TelemetryManager.getTracer(ijentMetricsScope) }
  internal val ijentMeter by lazy { TelemetryManager.getMeter(ijentMetricsScope) }

  internal val eventsCounter: AtomicLong = AtomicLong()

  internal val extendedFsEventsCounter: Map<FsEventKey, AtomicLong> = FsEventKey.VALUES.associateWith { AtomicLong() }
  internal val extendedFsEventsDurationNanos: Map<FsEventKey, AtomicLong> = FsEventKey.VALUES.associateWith { AtomicLong() }
  internal val extendedFsEventsRepeatIntervalNanos: Map<FsEventKey, AtomicLong> = FsEventKey.VALUES.associateWith { AtomicLong() }

  @Suppress("EnumEntryName")
  enum class DelegateType() {
    local,
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
        if (clazz.name == "com.intellij.docker.ijent.DockerMountsAwareFileSystemProvider") return docker
        if (!RoutingAwareFileSystemProvider::class.java.isAssignableFrom(clazz)) return local
        error("Unknown delegate class: ${clazz.name}")
      }
    }
  }

  data class FsEventKey(
    val delegateType: DelegateType,
    val operation: Operation,
    val success: Boolean?,
    val repeated: Boolean?
  ) {
    override fun toString(): String {
      val successKey = when (success) {
        true -> ".success"
        false -> ".failure"
        null -> ""
      }
      val repeatedKey = when (repeated) {
        true -> ".repeated"
        false -> ".initial"
        null -> ""
      }
      val delegateTypeKey = when (delegateType) {
        DelegateType.local -> ".local"
        DelegateType.wsl -> ".ijent.wsl"
        DelegateType.docker -> ".ijent.docker"
      }
      val keyString = "nio.fs${delegateTypeKey}.${operation}$successKey$repeatedKey"
      return keyString
    }
    companion object {
      val countUniquePathsEnabled: Boolean get() = System.getProperty("nio.mrfs.telemetry.count.unique.paths", "false").toBoolean()
      val VALUES: List<FsEventKey> = DelegateType.entries.filter {
        it != DelegateType.wsl || SystemInfo.isWindows
      }.flatMap { delegateType ->
        Operation.entries.flatMap { operation ->
          listOf(null, true, false).flatMap { success ->
            val repeatedChoices = if (countUniquePathsEnabled && success != null) listOf(null, true, false) else listOf(null)
            repeatedChoices.map { repeated ->
              FsEventKey(delegateType, operation, success, repeated)
            }
          }
        }
      }
    }
  }

  init {
    for ((key, getter) in dumpCounters()) {
      ijentMeter.counterBuilder(key).buildObserver().also {
        ijentMeter.batchCallback({ it.record(getter()) }, it)
      }
    }
  }

  fun dumpCounters(): List<Pair<String, () -> Long>> {
    return listOf("ijent.events.count" to {
      eventsCounter.get()
    }) + FsEventKey.VALUES.flatMap { key ->
      val keyString = key.toString()
      listOfNotNull("$keyString.count" to {
        extendedFsEventsCounter[key]!!.get()
      }, "$keyString.duration.ms" to {
        extendedFsEventsDurationNanos[key]!!.get().nanoseconds.inWholeMilliseconds
      }, ("$keyString.repeat.interval.ms" to {
        val intervalSum = extendedFsEventsRepeatIntervalNanos[key]!!.get().nanoseconds
        val uniqueCount = extendedFsEventsCounter[key.copy(repeated = false)]!!.get()
        (intervalSum.div(uniqueCount.coerceAtLeast(1).toDouble())).inWholeMilliseconds
      }).takeIf { key.repeated == true })
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

  val fsQueryStatCounter: FsQueryStatCounter? = if (FsEventKey.countUniquePathsEnabled) {
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
      repeated = repeated,
    )
    extendedFsEventsCounter[key]!!.incrementAndGet()
    extendedFsEventsDurationNanos[key]!!.addAndGet(Duration.between(startTime, endTime).toNanos())
    run {
      val keyWithNullRepeatedAndSuccess = key.copy(repeated = null, success = null)
      extendedFsEventsCounter[keyWithNullRepeatedAndSuccess]!!.incrementAndGet()
      extendedFsEventsDurationNanos[keyWithNullRepeatedAndSuccess]!!.addAndGet(Duration.between(startTime, endTime).toNanos())
    }
    if (repeated != null) {
      val keyWithNullRepeated = key.copy(repeated = null)
      extendedFsEventsCounter[keyWithNullRepeated]!!.incrementAndGet()
      extendedFsEventsDurationNanos[keyWithNullRepeated]!!.addAndGet(Duration.between(startTime, endTime).toNanos())
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