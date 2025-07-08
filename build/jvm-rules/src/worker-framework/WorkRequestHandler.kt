// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm

import com.google.protobuf.CodedOutputStream
import io.netty.buffer.ByteBufAllocator
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.logging.otlp.internal.traces.OtlpStdoutSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.semconv.ServiceAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.WorkRequestState.*
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.PrintWriter
import java.io.Writer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess

fun interface WorkRequestExecutor<T : WorkRequest> {
  suspend fun execute(request: T, writer: Writer, baseDir: Path, tracer: Tracer): Int
}

@Suppress("SpellCheckingInspection")
fun configureOpenTelemetry(@Suppress("unused") out: OutputStream, serviceName: String): Pair<OpenTelemetrySdk, () -> Unit> {
  val spanExporter = OtlpStdoutSpanExporter.builder()
    .setOutput(out)
    .setMemoryMode(MemoryMode.REUSABLE_DATA)
    .build()
  //val spanExporter = OtlpHttpSpanExporter.builder()
  //  .setMemoryMode(MemoryMode.REUSABLE_DATA)
  //  .setEndpoint("https://jaeger-dev.labs.jb.gg/v1/traces")
  //  .build()

  val batchSpanProcessor = BatchSpanProcessor.builder(spanExporter)
    .build()

  val resource = Resource.create(
    Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName)
  )
  val tracerProvider = SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(Sampler.alwaysOn())
    .addSpanProcessor(batchSpanProcessor)
    .build()

  val openTelemetrySdk = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .build()
  return openTelemetrySdk to {
    spanExporter.close()
    batchSpanProcessor.close()
  }
}

private val noopTracer = OpenTelemetry.noop().getTracer("noop")

fun <T : WorkRequest> processRequests(
  startupArgs: Array<String>,
  serviceName: String?,
  reader: WorkRequestReader<T>,
  executorFactory: (Tracer, CoroutineScope) -> WorkRequestExecutor<T>,
) {
  if (!startupArgs.contains("--persistent_worker")) {
    System.err.println("Only persistent worker mode is supported")
    exitProcess(3)
  }

  var onClose = {}
  var exitCode: Int
  try {
    val parallelism = Runtime.getRuntime().availableProcessors()
      .coerceAtLeast(2)  // Note(k15tfu): as per https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-default.html, and
      .coerceAtMost(14)  // as per https://youtrack.jetbrains.com/issue/IJI-2888
    runBlocking(Dispatchers.Default.limitedParallelism(parallelism) + OpenTelemetryContextElement(Context.root())) {
      val tracer = if (serviceName == null) {
        System.err.println("worker started (no OpenTelemetry)")
        noopTracer
      }
      else {
        val otAndOnClose = configureOpenTelemetry(out = System.err, serviceName = serviceName)
        onClose = otAndOnClose.second
        System.err.println("worker started (serviceName=$serviceName)")
        otAndOnClose.first.getTracer(serviceName)
      }

      tracer.span("process requests") { span ->
        val executor = executorFactory(tracer, this@runBlocking)
        WorkRequestHandler(requestExecutor = executor, out = System.out, tracer = tracer)
          .processRequests(reader)
      }
      exitCode = 0
    }
  }
  catch (_: CancellationException) {
    exitCode = 2
  }
  catch (e: Throwable) {
    e.printStackTrace(System.err)
    exitCode = 1
  }
  finally {
    onClose()
  }

  exitProcess(exitCode)
}

private class RequestState<T : WorkRequest>(
  @JvmField val request: T,
  @JvmField val job: Job,
) {
  @JvmField
  val state = AtomicReference(NOT_STARTED)
}

@VisibleForTesting
internal enum class WorkRequestState {
  NOT_STARTED, STARTED, FINISHED
}

/**
 * A helper class that handles [WorkRequests](https://bazel.build/docs/persistent-workers), including
 * [multiplex workers](https://bazel.build/docs/multiplex-worker).
 */
internal class WorkRequestHandler<T : WorkRequest> internal constructor(
  /**
   * The function to be called after each [WorkRequest] is read.
   */
  private val requestExecutor: WorkRequestExecutor<T>,

  private val out: OutputStream,

  /**
   * Must be quick and safe - executed in a read thread
   */
  private val cancelHandler: ((Int) -> Unit)? = null,
  private val tracer: Tracer,
) {
  private val workingDir = Path.of(".").toAbsolutePath().normalize()

  /**
   * Requests that are currently being processed.
   */
  private val activeRequests = ConcurrentHashMap<Int, RequestState<T>>()

  @OptIn(DelicateCoroutinesApi::class)
  internal suspend fun processRequests(reader: WorkRequestReader<T>) {
    try {
      val isTracingEnabled = tracer != noopTracer
      // Bazel already ensures that tasks are submitted at a manageable rate (ensures the system isn’t overwhelmed),
      // no need to use a channel
      tracer.span("read requests") { span ->
        coroutineScope {
          while (coroutineContext.isActive) {
            val request = try {
              runInterruptible(Dispatchers.IO) {
                reader.readWorkRequestFromStream()
              }
            }
            catch (e: InterruptedIOException) {
              span.recordException(e)
              null
            }

            if (request == null) {
              span.addEvent("stop processing - no more requests")
              return@coroutineScope
            }

            val requestId = request.requestId

            if (isTracingEnabled) {
              span.addEvent("request received", Attributes.of(
                AttributeKey.longKey("id"), requestId.toLong(),
                AttributeKey.booleanKey("cancel"), request.cancel,
              ))
            }

            if (request.cancel) {
              // Theoretically, we could have gotten two singleplex requests, and we can't tell those apart.
              // However, that's a violation of the protocol, so we don't try to handle it
              // (not least because handling it would be quite error-prone).
              val item = activeRequests.get(requestId)
              val stateRef = item?.state
              if (stateRef != null) {
                if (stateRef.compareAndSet(STARTED, FINISHED) || stateRef.compareAndSet(NOT_STARTED, FINISHED)) {
                  if (cancelHandler != null) {
                    cancelHandler(request.requestId)
                  }

                  item.job.cancel()
                  span.addEvent("request cancelled before handling", Attributes.of(AttributeKey.longKey("id"), requestId.toLong()))
                  writeRequest(requestId = requestId, wasCancelled = true) { true }
                }
              }
            }
            else {
              if (requestId == 0) {
                span.addEvent("waiting for singleplex requests to finish")
                while (activeRequests.containsKey(0)) {
                  // Previous singleplex requests can still be in activeRequests for a bit after the response has been sent.
                  // We need to wait for them to vanish.
                  delay(1)
                }
                span.addEvent("end of waiting for singleplex requests to finish")
              }

              val requestJob = launch(start = CoroutineStart.LAZY) {
                val item = activeRequests.get(requestId) ?: return@launch
                try {
                  checkStateAndExecuteRequest(item.state, request)
                }
                catch (e: CancellationException) {
                  ensureActive()

                  // ok, only this task was canceled
                  span.recordException(e, Attributes.of(
                    AttributeKey.stringKey("message"), "request cancelled",
                    AttributeKey.longKey("id"), requestId.toLong(),
                  ))
                }
              }

              val item = RequestState(request = request, job = requestJob)
              val previous = activeRequests.putIfAbsent(requestId, item)
              require(previous == null) {
                "Request still active: $requestId"
              }
              requestJob.start()
            }
          }
        }
      }
    }
    finally {
      tracer.span("cancelRequests on shutdown") { span ->
        for (item in activeRequests.values) {
          cancelRequestOnShutdown(item, span)
        }
        activeRequests.clear()
      }
    }
  }

  private suspend fun checkStateAndExecuteRequest(stateRef: AtomicReference<WorkRequestState>, request: T) {
    if (stateRef.compareAndSet(NOT_STARTED, STARTED)) {
      if (request.verbosity == 0) {
        executeRequest(request = request, requestState = stateRef, parentSpan = null, tracer = noopTracer)
      }
      else {
        tracer.spanBuilder("execute request")
          .setAttribute(AttributeKey.stringArrayKey("arguments"), request.arguments.toList())
          .setAttribute("id", request.requestId.toLong())
          .setAttribute("sandboxDir", request.sandboxDir ?: "")
          .use {
            executeRequest(request = request, requestState = stateRef, parentSpan = it, tracer = tracer)
          }
      }
    }
    else {
      val state = stateRef.get()
      if (state != FINISHED) {
        throw IllegalStateException("Already started (state=$state)")
      }
    }
  }

  private val outputWriteMutex = Mutex()

  private suspend inline fun writeRequest(
    requestId: Int,
    exitCode: Int = 0,
    outString: String? = null,
    wasCancelled: Boolean = false,
    beforeWrite: () -> Boolean,
  ) {
    var size = 0
    if (exitCode != 0) {
      size += CodedOutputStream.computeInt32Size(1, exitCode)
    }
    if (outString != null) {
      size += CodedOutputStream.computeStringSize(2, outString)
    }
    size += CodedOutputStream.computeInt32Size(3, requestId)
    if (wasCancelled) {
      size += CodedOutputStream.computeBoolSize(4, true)
    }

    val messageSizeWithSizePrefix = CodedOutputStream.computeUInt32SizeNoTag(size) + size
    val buffer = ByteBufAllocator.DEFAULT.heapBuffer(messageSizeWithSizePrefix, messageSizeWithSizePrefix)
    try {
      val codedOutput = CodedOutputStream.newInstance(buffer.array(), buffer.arrayOffset(), messageSizeWithSizePrefix)
      codedOutput.writeUInt32NoTag(size)
      if (exitCode != 0) {
        codedOutput.writeInt32(1, exitCode)
      }
      if (outString != null) {
        codedOutput.writeString(2, outString)
      }
      codedOutput.writeInt32(3, requestId)
      if (wasCancelled) {
        codedOutput.writeBool(4, true)
      }

      outputWriteMutex.withLock {
        if (!beforeWrite()) {
          activeRequests.remove(requestId)
          return@withLock
        }

        try {
          runInterruptible(Dispatchers.IO) {
            out.write(buffer.array(), buffer.arrayOffset(), messageSizeWithSizePrefix)
            out.flush()
          }
        }
        finally {
          activeRequests.remove(requestId)
        }
      }
    }
    finally {
      buffer.release()
    }
  }

  internal suspend fun executeRequest(
    request: T,
    requestState: AtomicReference<WorkRequestState>,
    parentSpan: Span?,
    tracer: Tracer,
  ) {
    val stringWriter = StringBuilderWriter()
    var errorToThrow: Throwable? = null
    val exitCode = try {
      tracer.span("requestExecutor.execute") {
        val baseDir = if (request.sandboxDir.isNullOrEmpty()) workingDir else workingDir.resolve(request.sandboxDir)
        requestExecutor.execute(request = request, writer = stringWriter, baseDir = baseDir, tracer = tracer)
      }
    }
    catch (e: CancellationException) {
      errorToThrow = e
      -1
    }
    catch (e: Throwable) {
      PrintWriter(stringWriter).use { e.printStackTrace(it) }
      if (e is Error) {
        errorToThrow = e
      }
      -1
    }

    val outString = stringWriter.toString()
    writeRequest(requestId = request.requestId, exitCode = exitCode, outString = outString.takeIf { it.isNotEmpty() }) {
      if (requestState.compareAndSet(STARTED, FINISHED)) {
        true
      }
      else {
        parentSpan?.addEvent(
          "request state was modified during processing",
          Attributes.of(AttributeKey.stringKey("state"), requestState.get().name),
        )
        false
      }
    }
    if (request.verbosity > 0) {
      parentSpan?.addEvent(
        "request processed",
        Attributes.of(AttributeKey.longKey("exitCode"), exitCode.toLong(), AttributeKey.stringKey("out"), outString),
      )
    }

    if (errorToThrow != null) {
      throw errorToThrow
    }
  }

  private suspend fun cancelRequestOnShutdown(item: RequestState<T>, span: Span) {
    val stateRef = item.state
    val requestId = item.request.requestId
    if (stateRef.compareAndSet(NOT_STARTED, FINISHED) || stateRef.compareAndSet(STARTED, FINISHED)) {
      span.addEvent(
        "request failed because it was not handled due to worker being forced to exit",
        Attributes.of(AttributeKey.longKey("request"), requestId.toLong()),
      )
      writeRequest(requestId = requestId, exitCode = 2) { true }
    }
    else {
      val state = stateRef.get()
      if (state != FINISHED) {
        throw IllegalStateException("Already started (state=$state)")
      }
    }
  }
}

private class StringBuilderWriter : Writer() {
  private val sb = StringBuilder()

  override fun write(cbuf: CharArray?) {
    synchronized(lock) {
      sb.append(cbuf)
    }
  }

  override fun write(c: Int) {
    synchronized(lock) {
      sb.append(c)
    }
  }

  override fun write(cbuf: CharArray, off: Int, len: Int) {
    if (len != 0) {
      synchronized(lock) {
        sb.append(cbuf, off, len)
      }
    }
  }

  override fun write(str: String?) {
    if (!str.isNullOrEmpty()) {
      synchronized(lock) {
        sb.append(str)
      }
    }
  }

  override fun write(str: String?, off: Int, len: Int) {
    if (len != 0) {
      synchronized(lock) {
        sb.append(str, off, off + len)
      }
    }
  }

  override fun append(csq: CharSequence): StringBuilderWriter {
    synchronized(lock) {
      sb.append(csq)
    }
    return this
  }

  override fun append(csq: CharSequence?, start: Int, end: Int): StringBuilderWriter {
    synchronized(lock) {
      sb.append(csq, start, end)
    }
    return this
  }

  override fun append(c: Char): StringBuilderWriter {
    synchronized(lock) {
      sb.append(c)
    }
    return this
  }

  override fun toString(): String {
    synchronized(lock) {
      if (sb.isEmpty()) {
        return ""
      }
      return sb.toString()
    }
  }

  override fun flush() {
  }

  override fun close() {
  }
}