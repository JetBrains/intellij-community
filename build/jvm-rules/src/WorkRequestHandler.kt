// Copyright 2020 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm

import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.io.PrintWriter
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest
import java.io.IOException
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse
import io.netty.buffer.ByteBufAllocator
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter
import java.io.Writer
import java.lang.AutoCloseable
import java.lang.Exception
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * A helper class that handles [WorkRequests](https://bazel.build/docs/persistent-workers), including
 * [multiplex workers](https://bazel.build/docs/multiplex-worker).
 */
class WorkRequestHandler internal constructor(
  /**
   * The function to be called after each [WorkRequest] is read.
   */
  private val executor: (WorkRequest, Writer) -> Int,
  /**
   * This worker's stderr.
   */
  private val errorStream: PrintStream = System.err,
  private val messageProcessor: ProtoWorkerMessageProcessor = ProtoWorkerMessageProcessor(input = System.`in`, output = System.out),
  /**
   * Must be quick and safe - executed in a read thread
   */
  private val cancelHandler: ((Int) -> Unit)? = null,
) {
  /**
   * Requests that are currently being processed. Visible for testing.
   */
  private val activeRequests = ConcurrentHashMap<Int, AtomicReference<Thread>>()
  // see https://stackoverflow.com/questions/70174468/project-loom-what-happens-when-virtual-thread-makes-a-blocking-system-call
  // but https://github.com/oracle/graal/issues/9939
  private val threadPool = Executors.newCachedThreadPool()

  /**
   * Runs an infinite loop of reading [WorkRequest] from `in`, running the callback, then writing the corresponding [WorkResponse] to `out`.
   * If there is an error reading or writing the requests or responses, it writes an error message on `err` and returns.
   * If `in` reaches EOF, it also returns.
   *
   * This function also wraps the system streams in a [WorkerIo] instance that prevents the underlying tool from writing to [System.out]
   * or reading from [System.in], which would corrupt the worker protocol.
   * When the while loop exits, the original system streams will be swapped back into [System].
   */
  fun processRequests() {
    // wrap the system streams into a WorkerIO instance to prevent unexpected reads and writes on stdin/stdout
    val workerIo = wrapStandardSystemStreams()
    try {
      // separate thread to be able to interrupt reading `readWorkRequest` (InputStream.read)
      startRead(workerIo, finishTimeout = 1.hours).join()
    }
    finally {
      threadPool.shutdownNow()

      try {
        // unwrap the system streams placing the original streams back
        workerIo.close()
      }
      catch (e: Exception) {
        errorStream.println(e.message)
      }
    }
  }

  internal fun stopAndAwait() {
    threadPool.shutdown()
    threadPool.awaitTermination(10, TimeUnit.SECONDS)
  }

  internal fun stopNow() {
    threadPool.shutdownNow()
  }

  internal fun startRead(workerIo: WorkerIo, finishTimeout: Duration): Thread {
    return Thread.ofVirtual()
      .name("request reader")
      .uncaughtExceptionHandler { _, e ->
        errorStream.println("error reading next request: $e")
        e.printStackTrace(errorStream)
      }
      .start {
        val readThread = Thread.currentThread()
        while (!threadPool.isShutdown) {
          val request = messageProcessor.readWorkRequest() ?: break
          @Suppress("UsePropertyAccessSyntax")
          if (request.getCancel()) {
            handleCancelRequest(request)
          }
          else {
            scheduleHandlingRequest(workerIo = workerIo, request = request, readThread = readThread)
          }
        }

        threadPool.shutdown()
        // after processing all requests (readWorkRequest returns `null`), wait for finishing other tasks
        val finished = threadPool.awaitTermination(finishTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        if (!finished) {
          errorStream.println("cannot await termination in $finishTimeout")
          threadPool.shutdownNow()
        }
      }
  }

  /**
   * Starts a thread for the given request.
   */
  private fun scheduleHandlingRequest(workerIo: WorkerIo, request: WorkRequest, readThread: Thread) {
    if (request.requestId == 0) {
      while (activeRequests.containsKey(0)) {
        // Previous singleplex requests can still be in activeRequests for a bit after the response has been sent.
        // We need to wait for them to vanish.
        try {
          Thread.sleep(1)
        }
        catch (_: InterruptedException) {
          // reset interrupted status
          Thread.interrupted()
          return
        }
      }
    }

    val requestStateRef = AtomicReference(NOT_STARTED)
    val previous = activeRequests.putIfAbsent(request.requestId, requestStateRef)
    require(previous == null) {
      "Request still active: ${request.requestId}"
    }
    threadPool.execute {
      val isFatalErrorOccurred = callHandleRequest(requestStateRef, workerIo, request)
      if (isFatalErrorOccurred) {
        try {
          // shutdown the worker in case of severe issues,
          // we don't handle RuntimeException here, as those are not serious enough to merit shutting down the worker.
          if (!threadPool.isShutdown) {
            errorStream.println("error thrown by worker thread, shutting down worker")
            threadPool.shutdownNow()
          }
        }
        finally {
          readThread.interrupt()
        }
      }
    }
  }

  private fun callHandleRequest(
    requestStateRef: AtomicReference<Thread>,
    workerIo: WorkerIo,
    request: WorkRequest
  ): Boolean {
    try {
      if (requestStateRef.compareAndSet(NOT_STARTED, Thread.currentThread())) {
        return handleRequest(workerIo = workerIo, request = request, requestState = requestStateRef)
      }
      else if (requestStateRef.compareAndSet(CANCELLED, FINISHED)) {
        val response = WorkResponse.newBuilder()
          .setRequestId(request.requestId)
          .setWasCancelled(true)
          .build()
        synchronized(this) {
          messageProcessor.writeWorkResponse(response)
        }
        return false
      }
      else {
        val state = requestStateRef.get()
        if (state == FINISHED) {
          return false
        }
        else {
          throw IllegalStateException("Already started (state=$state)")
        }
      }
    }
    catch (_: InterruptedException) {
      requestStateRef.set(FINISHED)
      // reset interrupted status
      Thread.interrupted()
      return false
    }
    catch (e: Throwable) {
      e.printStackTrace(errorStream)
      return e is Error
    }
    finally {
      activeRequests.remove(request.requestId)
    }
  }

  /**
   * Handles and responds to the given [WorkRequest].
   *
   * @throws IOException if there is an error talking to the server. Errors from calling the [][.callback] are reported with exit code 1.
   */
  // visible for tests
  internal fun handleRequest(workerIo: WorkerIo, request: WorkRequest, requestState: AtomicReference<Thread>): Boolean {
    var exitCode = 1
    val stringWriter = StringWriter()
    var isFatalErrorOccurred = false
    stringWriter.use { writer ->
      try {
        exitCode = executor(request, writer)
      }
      catch (_: InterruptedException) {
        // reset interrupted status
        Thread.interrupted()
      }
      catch (e: Throwable) {
        PrintWriter(writer).use { e.printStackTrace(it) }
        if (e is Error) {
          isFatalErrorOccurred = true
        }
      }

      try {
        // read out the captured string for the final WorkResponse output
        val captured = workerIo.readCapturedAsUtf8String().trim()
        if (!captured.isEmpty()) {
          writer.write(captured)
        }
      }
      catch (e: Throwable) {
        errorStream.println(e.message)
      }
    }

    val responseBuilder = WorkResponse.newBuilder()
    responseBuilder.setRequestId(request.requestId)
    if (requestState.getAndSet(FINISHED) == CANCELLED) {
      responseBuilder.setWasCancelled(true)
    }
    else {
      responseBuilder.setOutput(responseBuilder.getOutput() + stringWriter).setExitCode(exitCode)
    }
    val response = responseBuilder.build()
    synchronized(this) {
      messageProcessor.writeWorkResponse(response)
    }

    return isFatalErrorOccurred
  }

  /**
   * Marks the given request as canceled and uses [cancelHandler] to request cancellation.
   *
   * For simplicity, and to avoid blocking in [cancelHandler], response to cancellation
   * is still handled by [handleRequest] once the canceled request aborts (or finishes).
   */
  private fun handleCancelRequest(request: WorkRequest) {
    // Theoretically, we could have gotten two singleplex requests, and we can't tell those apart.
    // However, that's a violation of the protocol, so we don't try to handle it (not least because handling it would be quite error-prone).
    val requestToCancel = activeRequests.get(request.requestId) ?: return
    val currentState = requestToCancel.get()
    if (currentState != FINISHED && currentState != CANCELLED && requestToCancel.compareAndSet(currentState, CANCELLED)) {
      // Response will be sent from request thread once request handler returns.
      // We can ignore any exceptions in cancel callback since it's best effort.
      if (cancelHandler != null) {
        cancelHandler(request.requestId)
      }
    }
  }
}

private val NOT_STARTED = Thread.ofVirtual().name("not_started").unstarted { }
private val CANCELLED = Thread.ofVirtual().name("cancelled").unstarted { }
private val FINISHED = Thread.ofVirtual().name("finished").unstarted { }

/**
 * A class that wraps the standard [System.in], [System.out], and [System.err]
 * with our own ByteArrayOutputStream that allows [WorkRequestHandler] to safely capture
 * outputs that can't be directly captured by the PrintStream associated with the work request.
 *
 * This is most useful when integrating JVM tools that write exceptions and logs directly to
 * [System.out] and [System.err], which would corrupt the persistent worker protocol.
 * We also redirect [System.in], just in case a tool should attempt to read it.
 *
 * WorkerIO implements [AutoCloseable] and will swap the original streams back into [System] once close has been called.
 */
internal class WorkerIo
/**
 * Creates a new [WorkerIo] that allows [WorkRequestHandler] to capture standard
 * output and error streams that can't be directly captured by the PrintStream associated with
 * the work request.
 */ /* visible for test */(
  /**
   * Returns the original input stream most commonly provided by [System.in]
   */
  @JvmField val originalInputStream: InputStream?,
  /**
   * Returns the original output stream most commonly provided by [System.out]
   */
  @JvmField val originalOutputStream: PrintStream?,
  /**
   * Returns the original error stream most commonly provided by [System.err]
   */
  @JvmField val originalErrorStream: PrintStream?,
  private val capturedStream: ByteArrayOutputStream,
  private val restore: AutoCloseable
) : AutoCloseable {
  /**
   * Returns the captured outputs as a UTF-8 string
   */
  fun readCapturedAsUtf8String(): String {
    capturedStream.flush()
    val captureOutput = capturedStream.toString(StandardCharsets.UTF_8)
    capturedStream.reset()
    return captureOutput
  }

  override fun close() {
    restore.close()
  }
}

/**
 * Wraps the standard System streams and WorkerIO instance
 */
internal fun wrapStandardSystemStreams(): WorkerIo {
  // save the original streams
  val originalInputStream = System.`in`
  val originalOutputStream = System.out
  val originalErrorStream = System.err

  // replace the original streams with our own instances
  val capturedStream = ByteArrayOutputStream()
  val outputBuffer = PrintStream(capturedStream, true)
  val byteArrayInputStream = ByteArray(0).inputStream()
  System.setIn(byteArrayInputStream)
  System.setOut(outputBuffer)
  System.setErr(outputBuffer)

  return WorkerIo(
    originalInputStream = originalInputStream,
    originalOutputStream = originalOutputStream,
    originalErrorStream = originalErrorStream,
    capturedStream = capturedStream,
    restore = AutoCloseable {
      System.setIn(originalInputStream)
      System.setOut(originalOutputStream)
      System.setErr(originalErrorStream)
      outputBuffer.close()
      byteArrayInputStream.close()
    })
}