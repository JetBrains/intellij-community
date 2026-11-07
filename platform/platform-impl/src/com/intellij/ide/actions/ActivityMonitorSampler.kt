// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import jdk.jfr.consumer.RecordingStream
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiFunction
import java.util.function.Supplier

@ApiStatus.Internal
class ActivityMonitorSampler(
  private val classify: BiFunction<String, List<String>, String>,
  private val createRecording: Supplier<RecordingStream>,
) : AutoCloseable {
  constructor(classify: BiFunction<String, List<String>, String>) : this(classify, ::RecordingStream)

  private var stream: RecordingStream? = null
  @Volatile
  private var closed: Boolean = false
  private val samples: MutableMap<String, Long> = HashMap()

  @Synchronized
  fun start(): Boolean {
    if (closed) return false
    if (stream != null) return true
    try {
      val recording = createRecording.get()
      try {
        val errorReported = AtomicBoolean()
        recording.onError { e ->
          rethrowControlFlowException(e)
          if (errorReported.compareAndSet(false, true)) {
            Logger.getInstance(ActivityMonitorSampler::class.java).warn("Cannot process Activity Monitor sample", e)
          }
        }
        recording.onClose { closed = true }
        recording.setMaxAge(Duration.ofSeconds(10))
        recording.setMaxSize(16 * 1024 * 1024)
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20))
        recording.onEvent("jdk.ExecutionSample") { event ->
          if (closed) return@onEvent
          val thread = event.getThread("sampledThread") ?: return@onEvent
          val stack = event.stackTrace ?: return@onEvent
          val subsystem = classify.apply(thread.javaName, stack.frames.map { it.method.type.name })
          synchronized(samples) {
            samples[subsystem] = (samples[subsystem] ?: 0) + 1
          }
        }
        recording.startAsync()
        stream = recording
        return !closed
      }
      finally {
        if (stream == null) recording.close()
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      closed = true
      Logger.getInstance(ActivityMonitorSampler::class.java).warn("Cannot start Activity Monitor sampling", e)
      return false
    }
  }

  fun takeSamples(): Map<String, Long> = synchronized(samples) {
    val snapshot = samples.toMap()
    samples.clear()
    snapshot
  }

  fun closeAsync() {
    closed = true
    AppExecutorUtil.getAppExecutorService().execute { close() }
  }

  override fun close() {
    val recording = synchronized(this) {
      closed = true
      stream.also { stream = null }
    }
    recording?.close()
  }
}
