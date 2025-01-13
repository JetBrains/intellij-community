// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm

import com.google.protobuf.CodedOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.WorkRequestState.*
import org.jetbrains.bazel.jvm.logging.LogEvent
import org.jetbrains.bazel.jvm.logging.LogWriter
import java.io.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess

fun interface WorkRequestExecutor {
  suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path): Int
}

fun processRequests(
  startupArgs: Array<String>,
  executor: WorkRequestExecutor,
  setup: (LogWriter) -> Unit = {},
) {
  if (!startupArgs.contains("--persistent_worker")) {
    System.err.println("Only persistent worker mode is supported")
    exitProcess(1)
  }

  try {
    runBlocking(Dispatchers.Default) {
      val log = LogWriter(this, System.err)
      try {
        setup(log)

        WorkRequestHandler(requestExecutor = executor, input = System.`in`, out = System.out, log = log)
          .processRequests()
      }
      catch (e: CancellationException) {
        log.log(LogEvent(message = "cancelled", exception = e))
        throw e
      }
      catch (e: Throwable) {
        log.log(LogEvent(message = "internal error", exception = e))
      }
      finally {
        log.shutdown()
      }
    }
  }
  catch (e: Throwable) {
    e.printStackTrace(System.err)
    exitProcess(1)
  }

  exitProcess(0)
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
  private val log: LogWriter? = null,
) {
  private val workingDir = Path.of(".").toAbsolutePath().normalize()

  /**
   * Requests that are currently being processed.
   */
  private val activeRequests = ConcurrentHashMap<Int, RequestState>()

  @OptIn(DelicateCoroutinesApi::class)
  internal suspend fun processRequests() {
    val requestChannel = Channel<RequestState>(Channel.UNLIMITED)
    try {
      coroutineScope {
        startTaskProcessing(requestChannel)

        readRequests(requestChannel)
      }
    }
    finally {
      withContext(NonCancellable) {
        if (!requestChannel.isClosedForSend) {
          requestChannel.close()
        }

        //logger?.log("Unprocessed requests: ${activeRequests.keys.joinToString(", ")}")

        for (item in requestChannel) {
          cancelRequestOnShutdown(item)
        }
        for (item in activeRequests.values) {
          cancelRequestOnShutdown(item)
        }
        activeRequests.clear()
      }
    }
  }

  private suspend fun readRequests(requestChannel: Channel<RequestState>) {
    val inputListToReuse = ArrayList<Input>()
    val argListToReuse = ArrayList<String>()
    while (coroutineContext.isActive) {
      val request = try {
        runInterruptible(Dispatchers.IO) {
          readWorkRequestFromStream(input, inputListToReuse, argListToReuse)
        }
      }
      catch (e: InterruptedIOException) {
        log?.info("stop processing", e)
        null
      }

      if (request == null) {
        log?.info("stop processing - no more requests")
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

  private fun CoroutineScope.startTaskProcessing(requestChannel: Channel<RequestState>) {
    repeat(Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) {
      launch {
        for (item in requestChannel) {
          //logger?.log("request(id=${item.request.requestId}) started to execute")
          val stateRef = item.state
          when {
            stateRef.compareAndSet(NOT_STARTED, STARTED) -> {
              handleRequest(request = item.request, requestState = stateRef)
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

  private suspend fun cancelRequestOnShutdown(item: RequestState) {
    val stateRef = item.state
    val requestId = item.request.requestId
    if (stateRef.compareAndSet(NOT_STARTED, FINISHED) || stateRef.compareAndSet(STARTED, FINISHED)) {
      //logger?.log("request(id=$requestId}) failed because it was not handled due to worker being forced to exit")
      writeAndRemoveRequest(requestId = requestId, exitCode = 2)
    }
    else {
      val state = stateRef.get()
      if (state != FINISHED) {
        throw IllegalStateException("Already started (state=$state)")
      }
    }
  }

  /**
   * Handles and responds to the given [WorkRequest].
   *
   * @throws IOException if there is an error talking to the server. Errors from calling the [][.callback] are reported with exit code 1.
   */
  // visible for tests
  internal suspend fun handleRequest(request: WorkRequest, requestState: AtomicReference<WorkRequestState>) {
    val baseDir = if (request.sandboxDir.isNullOrEmpty()) workingDir else workingDir.resolve(request.sandboxDir)
    var exitCode = 1
    val stringWriter = StringBuilderWriter()
    var errorToThrow: Throwable? = null
    val requestId = request.requestId
    try {
      if (request.verbosity > 0) {
        log?.log(LogEvent(
          message = "execute request",
          context = arrayOf(
            "id", requestId,
            "baseDir", baseDir,
            "inputs", request.inputs.asSequence().map { it.path },
          ),
        ))
      }
      exitCode = requestExecutor.execute(request = request, writer = stringWriter, baseDir = baseDir)
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

    withContext(NonCancellable) {
      if (!requestState.compareAndSet(STARTED, FINISHED)) {
        if (request.verbosity > 0) {
          log?.info("request state was modified during processing", arrayOf("id", requestId, "state", requestState.get()))
        }
        return@withContext
      }

      val outString = stringWriter.toString()
      writeAndRemoveRequest(
        requestId = requestId,
        exitCode = exitCode,
        outString = outString.takeIf { it.isNotEmpty() },
      )

      if (request.verbosity > 0) {
        log?.log(LogEvent(
          message = "request processed",
          context = arrayOf("id", requestId, "exitCode", exitCode, "out", outString),
        ))
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