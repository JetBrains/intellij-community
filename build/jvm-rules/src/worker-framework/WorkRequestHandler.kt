// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm

import com.google.protobuf.CodedOutputStream
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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.WorkRequestState.*
import java.io.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess

fun interface WorkRequestExecutor {
  suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracingContext: Context, tracer: Tracer): Int
}

fun configureOpenTelemetry(out: OutputStream, serviceName: String): OpenTelemetrySdk {
  val spanExporter = OtlpStdoutSpanExporter.builder()
    .setOutput(out)
    .setMemoryMode(MemoryMode.REUSABLE_DATA)
    .build()

  val batchSpanProcessor = BatchSpanProcessor.builder(spanExporter)
    .build()

  val resource = Resource.create(
    Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName)
  )
  val tracerProvider = SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(Sampler.alwaysOn())
    .addSpanProcessor(batchSpanProcessor) // For batch exporting (preferred in production)
    .build()

  // Build and set the OpenTelemetry SDK
  val openTelemetrySdk = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .build()
  return openTelemetrySdk
}

private val noopTracer = OpenTelemetry.noop().getTracer("noop")

fun processRequests(
  startupArgs: Array<String>,
  executor: WorkRequestExecutor,
  serviceName: String?,
  setup: (Tracer, CoroutineScope) -> Unit = { _, _  -> },
) {
  if (!startupArgs.contains("--persistent_worker")) {
    System.err.println("Only persistent worker mode is supported")
    exitProcess(1)
  }

  val tracer = if (serviceName == null) {
    noopTracer
  }
  else {
    configureOpenTelemetry(System.err, serviceName).getTracer(serviceName)
  }
  try {
    runBlocking(Dispatchers.Default) {
      tracer.spanBuilder("process requests").use { span ->
        setup(tracer, this@runBlocking)
        WorkRequestHandler(requestExecutor = executor, input = System.`in`, out = System.out, tracer = tracer)
          .processRequests(Context.current().with(span))
      }

      exitProcess(0)
    }
  }
  catch (e: Throwable) {
    e.printStackTrace(System.err)
    exitProcess(1)
  }
}

private class RequestState(@JvmField val request: WorkRequest) {
  @JvmField val state = AtomicReference<WorkRequestState>(NOT_STARTED)
}

@VisibleForTesting
internal enum class WorkRequestState {
  NOT_STARTED, STARTED, FINISHED
}

/**
 * A helper class that handles [WorkRequests](https://bazel.build/docs/persistent-workers), including
 * [multiplex workers](https://bazel.build/docs/multiplex-worker).
 */
internal class WorkRequestHandler internal constructor(
  /**
   * The function to be called after each [WorkRequest] is read.
   */
  private val requestExecutor: WorkRequestExecutor,

  private val input: InputStream,
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
  private val activeRequests = ConcurrentHashMap<Int, RequestState>()

  @OptIn(DelicateCoroutinesApi::class)
  internal suspend fun processRequests(tracingContext: Context) {
    val requestChannel = Channel<RequestState>(Channel.UNLIMITED)
    try {
      coroutineScope {
        tracer.spanBuilder("process requests").setParent(tracingContext).use { span ->
          startTaskProcessing(requestChannel, tracingContext.with(span))
        }

        tracer.spanBuilder("read requests").setParent(tracingContext).use { span ->
          readRequests(requestChannel, span)
        }
      }
    }
    finally {
      withContext(NonCancellable) {
        if (!requestChannel.isClosedForSend) {
          requestChannel.close()
        }

        //logger?.log("Unprocessed requests: ${activeRequests.keys.joinToString(", ")}")

        tracer.spanBuilder("cancelRequests on shutdown").setParent(tracingContext).use { span ->
          for (item in requestChannel) {
            cancelRequestOnShutdown(item, span)
          }
          for (item in activeRequests.values) {
            cancelRequestOnShutdown(item, span)
          }
          activeRequests.clear()
        }
      }
    }
  }

  private suspend fun readRequests(requestChannel: Channel<RequestState>, span: Span) {
    val inputPathsToReuse = ArrayList<String>()
    val inputDigestsToReuse = ArrayList<ByteArray>()
    val argListToReuse = ArrayList<String>()
    while (coroutineContext.isActive) {
      val request = try {
        runInterruptible(Dispatchers.IO) {
          readWorkRequestFromStream(input, inputPathsToReuse, inputDigestsToReuse, argListToReuse)
        }
      }
      catch (e: InterruptedIOException) {
        span.recordException(e)
        null
      }

      if (request == null) {
        span.addEvent("stop processing - no more requests")
        requestChannel.close()
        break
      }

      val requestId = request.requestId

      //logger?.log("request(id=$requestId${if (request.cancel) ", cancel=true" else ""}) start")
      if (request.cancel) {
        withContext(NonCancellable) {
          // Theoretically, we could have gotten two singleplex requests, and we can't tell those apart.
          // However, that's a violation of the protocol, so we don't try to handle it
          // (not least because handling it would be quite error-prone).
          val stateRef = activeRequests.get(requestId)?.state
          if (stateRef != null) {
            if (stateRef.compareAndSet(STARTED, FINISHED) || stateRef.compareAndSet(NOT_STARTED, FINISHED)) {
              if (cancelHandler != null) {
                cancelHandler(request.requestId)
              }
              //logger?.log("request(id=$requestId) cancelled before handling")
              writeAndRemoveRequest(requestId = requestId, wasCancelled = true)
            }
          }
        }
      }
      else {
        if (requestId == 0) {
          while (activeRequests.containsKey(0)) {
            // Previous singleplex requests can still be in activeRequests for a bit after the response has been sent.
            // We need to wait for them to vanish.
            delay(1)
          }
        }

        val item = RequestState(request = request)
        val previous = activeRequests.putIfAbsent(requestId, item)
        require(previous == null) {
          "Request still active: $requestId"
        }
        requestChannel.send(item)
      }
    }
  }

  private fun CoroutineScope.startTaskProcessing(requestChannel: Channel<RequestState>, tracingContext: Context) {
    repeat(Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) {
      launch {
        for (item in requestChannel) {
          val stateRef = item.state
          when {
            stateRef.compareAndSet(NOT_STARTED, STARTED) -> {
              val request = item.request
              val span = if (request.verbosity > 0) {
                tracer.spanBuilder("execute request")
                  .setAllAttributes(Attributes.of(
                    AttributeKey.stringArrayKey("arguments"), request.arguments.toList(),
                    //AttributeKey.stringArrayKey("inputs"), request.inputs.map { it.path },
                    AttributeKey.longKey("id"), request.requestId.toLong(),
                    AttributeKey.stringKey("sandboxDir"), request.sandboxDir?.toString() ?: "",
                  ))
                  .setParent(tracingContext)
                  .startSpan()
              }
              else {
                null
              }

              try {
                handleRequest(
                  request = request,
                  requestState = stateRef,
                  tracingContext = span?.let { tracingContext.with(it) },
                  parentSpan = span,
                )
              }
              catch (e: Throwable) {
                span?.recordException(e)
                throw e
              }
              finally {
                span?.end()
              }
            }
            else -> {
              val state = stateRef.get()
              if (state != FINISHED) {
                throw IllegalStateException("Already started (state=$state)")
              }
            }
          }
        }
      }
    }
  }

  private val outputWriteMutex = Mutex()

  private suspend fun writeAndRemoveRequest(requestId: Int, exitCode: Int = 0, outString: String? = null, wasCancelled: Boolean = false) {
    withContext(NonCancellable) {
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

      outputWriteMutex.withLock {
        val bufferSize = (CodedOutputStream.computeUInt32SizeNoTag(size) + size).coerceAtMost(4096)
        val codedOutput = CodedOutputStream.newInstance(out, bufferSize)
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
        codedOutput.flush()

        activeRequests.remove(requestId)
        out.flush()
      }
    }
  }

  private suspend fun cancelRequestOnShutdown(item: RequestState, span: Span) {
    val stateRef = item.state
    val requestId = item.request.requestId
    if (stateRef.compareAndSet(NOT_STARTED, FINISHED) || stateRef.compareAndSet(STARTED, FINISHED)) {
      span.addEvent(
        "request failed because it was not handled due to worker being forced to exit",
        Attributes.of(AttributeKey.longKey("request"), requestId.toLong()),
      )
      writeAndRemoveRequest(requestId = requestId, exitCode = 2)
    }
    else {
      val state = stateRef.get()
      if (state != FINISHED) {
        throw IllegalStateException("Already started (state=$state)")
      }
    }
  }

  internal suspend fun handleRequest(
    request: WorkRequest,
    requestState: AtomicReference<WorkRequestState>,
    tracingContext: Context?,
    parentSpan: Span?,
  ) {
    val baseDir = if (request.sandboxDir.isNullOrEmpty()) workingDir else workingDir.resolve(request.sandboxDir)
    var exitCode = 1
    val stringWriter = StringBuilderWriter()
    var errorToThrow: Throwable? = null
    val requestId = request.requestId
    val span = if (tracingContext == null) {
      null
    }
    else {
      tracer.spanBuilder("requestExecutor.execute").setParent(tracingContext).startSpan()
    }
    try {
      try {
        val tracingContext = if (tracingContext == null) Context.root() else tracingContext.with(span!!)
        exitCode = requestExecutor.execute(
          request = request,
          writer = stringWriter,
          baseDir = baseDir,
          tracingContext = tracingContext,
          tracer = if (tracingContext == null) noopTracer else tracer,
        )
      }
      finally {
        span?.end()
      }
    }
    catch (e: CancellationException) {
      errorToThrow = e
    }
    catch (e: Throwable) {
      PrintWriter(stringWriter).use { e.printStackTrace(it) }
      if (e is Error) {
        errorToThrow = e
      }
    }
    finally {
      span?.end()
    }

    withContext(NonCancellable) {
      if (!requestState.compareAndSet(STARTED, FINISHED)) {
        parentSpan?.addEvent(
          "request state was modified during processing",
          Attributes.of(AttributeKey.stringKey("state"), requestState.get().name),
        )
        return@withContext
      }

      val outString = stringWriter.toString()
      writeAndRemoveRequest(
        requestId = requestId,
        exitCode = exitCode,
        outString = outString.takeIf { it.isNotEmpty() },
      )

      if (request.verbosity > 0) {
        parentSpan?.addEvent(
          "request processed",
          Attributes.of(AttributeKey.longKey("exitCode"), exitCode.toLong(), AttributeKey.stringKey("out"), outString),
        )
      }
    }

    if (errorToThrow != null) {
      throw errorToThrow
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