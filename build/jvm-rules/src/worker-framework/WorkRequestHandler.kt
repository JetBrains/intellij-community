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
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess

fun interface WorkRequestExecutor {
  suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path): Int
}

internal fun createLogger(classifier: String, coroutineScope: CoroutineScope): AsyncFileLogger? {
  val dir = Path.of(System.getProperty("user.home"), "$classifier-worker")
  // changing env or flag leads to rebuild, so, we create logger if log dir exists
  if (!Files.isDirectory(dir)) {
    return null
  }
  return AsyncFileLogger(file = dir.resolve("log-" + System.currentTimeMillis() + ".txt"), coroutineScope = coroutineScope)
}

fun processRequests(startupArgs: Array<String>, executor: WorkRequestExecutor, debugLogClassifier: String? = null) {
  if (!startupArgs.contains("--persistent_worker")) {
    System.err.println("Only persistent worker mode is supported")
    exitProcess(1)
  }

  // wrap the system streams into a WorkerIO instance to prevent unexpected reads and writes on stdin/stdout
  try {
    runBlocking(Dispatchers.Default) {
      val logger = debugLogClassifier?.let { createLogger(classifier = debugLogClassifier, this) }
      if (logger != null) {
        coroutineContext.job.invokeOnCompletion {
          logger.log("main job completed (cause=$it)")
        }
      }

      try {
        WorkRequestHandler(requestExecutor = executor, input = System.`in`, out = System.out, logger = logger)
          .processRequests()
      }
      finally {
        logger?.shutdown()
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
  private val logger: AsyncFileLogger? = null,
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

        logger?.log("Unprocessed requests: ${activeRequests.keys.joinToString(", ")}")

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
        logger?.log("stop processing: $e")
        null
      }

      if (request == null) {
        logger?.log("stop processing - no more requests")
        requestChannel.close()
        break
      }

      val requestId = request.requestId

      logger?.log("request(id=$requestId${if (request.cancel) ", cancel=true" else ""}) start")
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
              logger?.log("request(id=$requestId) cancelled before handling")
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
          logger?.log("request(id=${item.request.requestId}) started to execute")
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
      logger?.log("request(id=$requestId}) failed because it was not handled due to worker being forced to exit")
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
    val stringWriter = StringWriter()
    var errorToThrow: Throwable? = null
    val requestId = request.requestId
    try {
      //System.err.println("request: ${request.inputs.asSequence().take(4).joinToString { it.path }}, $baseDir, ${request.sandboxDir}")
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
      if (requestState.compareAndSet(STARTED, FINISHED)) {
        logger?.log("request(id=$requestId) handled(exitCode=$exitCode)")
        writeAndRemoveRequest(
          requestId = requestId,
          exitCode = exitCode,
          outString = stringWriter.buffer.takeIf { it.isNotEmpty() }?.toString(),
        )
      }
      else {
        logger?.log("request(id=$requestId) state was changed to ${requestState.get()}")
      }
    }

    if (errorToThrow != null) {
      throw errorToThrow
    }
  }
}