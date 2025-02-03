// Copyright 2020 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm

import com.google.devtools.build.lib.worker.WorkerProtocol
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/**
 * Tests for the WorkRequestHandler
 */
class WorkRequestHandlerTest {
  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun normalWorkRequest() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ -> 1 },
      //input = ByteArrayInputStream(ByteArray(0)),
      out = out,
      tracer = OpenTelemetry.noop().getTracer("noop"),
    )

    val request = WorkRequest(
      arguments = arrayOf("--sources", "A.java"),
      inputPaths = emptyArray(),
      requestId = 0,
      cancel = false,
      verbosity = 0,
      sandboxDir = null,
    )
    runBlocking {
      handler.handleRequest(request = request, requestState = AtomicReference(WorkRequestState.STARTED), tracingContext = null, parentSpan = null)
    }

    val response = WorkResponse.parseDelimitedFrom(out.toByteArray().inputStream())
    assertThat(response.requestId).isEqualTo(0)
    assertThat(response.exitCode).isEqualTo(1)
    assertThat(response.getOutput()).isEmpty()
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun multiplexWorkRequest() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ -> 0 },
      //input = ByteArray(0).inputStream(),
      out,
      tracer = OpenTelemetry.noop().getTracer("noop"),
    )

    val request = newWorkRequest(listOf("--sources", "A.java"))
    runBlocking {
      handler.handleRequest(request = request, requestState = AtomicReference(WorkRequestState.STARTED), tracingContext = null, parentSpan = null)
    }

    val response = WorkResponse.parseDelimitedFrom(out.toByteArray().inputStream())
    assertThat(response.requestId).isEqualTo(42)
    assertThat(response.exitCode).isEqualTo(0)
    assertThat(response.getOutput()).isEmpty()
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  fun multiplexWorkRequestStopsWorkerOnException() {
    val src = PipedOutputStream()

    // work request threads release this when they have started
    val started = Semaphore(0)
    val workerThreads = AtomicInteger()
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ ->
        // each call to this, runs in its own thread
        workerThreads.incrementAndGet()
        started.release()
        if (workerThreads.get() < 2) {
          // this blocks forever
          while (coroutineContext.isActive) {
            delay(1)
          }
        }
        else {
          throw Error("Intentional death!")
        }
        0
      },
      out = OutputStream.nullOutputStream(),
      tracer = OpenTelemetry.noop().getTracer("noop"),
    )

    useHandler(handler = handler, input = PipedInputStream(src), waitForProcessing = true, errorFilter = { it.message != "Intentional death!" }) {
      val args = listOf("--sources", "A.java")
      WorkerProtocol.WorkRequest.newBuilder().addAllArguments(args).setRequestId(42).build().writeDelimitedTo(src)
      WorkerProtocol.WorkRequest.newBuilder().addAllArguments(args).setRequestId(43).build().writeDelimitedTo(src)

      started.acquire(2)
    }
    assertThat(workerThreads.get()).isEqualTo(2)
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun testOutput() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ ->
        err.appendLine("Failed!")
        1
      },
      //input = ByteArray(0).inputStream(),
      out,
      tracer = OpenTelemetry.noop().getTracer("noop"),
    )

    val args = listOf("--sources", "A.java")
    val request = newWorkRequest(args, requestId = 0)
    runBlocking {
      handler.handleRequest(request, AtomicReference(WorkRequestState.STARTED), tracingContext = null, parentSpan = null)
    }

    val response = WorkResponse.parseDelimitedFrom(out.toByteArray().inputStream())
    assertThat(response.requestId).isEqualTo(0)
    assertThat(response.exitCode).isEqualTo(1)
    assertThat(response.getOutput()).contains("Failed!")
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun testException() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ ->
        throw RuntimeException("Exploded!")
      },
      //input = ByteArray(0).inputStream(),
      out,
      tracer = OpenTelemetry.noop().getTracer("noop"),
    )

    val args = listOf("--sources", "A.java")
    val request = newWorkRequest(args, 342)
    runBlocking {
      handler.handleRequest(request = request, requestState = AtomicReference(WorkRequestState.STARTED), tracingContext = null, parentSpan = null)
    }

    val response = WorkResponse.parseDelimitedFrom(ByteArrayInputStream(out.toByteArray()))
    assertThat(response.requestId).isEqualTo(342)
    assertThat(response.exitCode).isEqualTo(1)
    assertThat(response.getOutput()).startsWith("java.lang.RuntimeException: Exploded!")
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun cancelRequestExactlyOneResponseSent() {
    var handlerCalled = false
    var cancelCalled = false
    val src = PipedOutputStream()
    val dest = PipedInputStream()

    val input = PipedInputStream(src)
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ ->
        handlerCalled = true
        err.appendLine("Such work! Much progress! Wow!")
        1
      },
      out = PipedOutputStream(dest),
      tracer = OpenTelemetry.noop().getTracer("noop"),
      cancelHandler = {
        cancelCalled = true
      },
    )

    useHandler(handler, input) {
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)

      val response = WorkResponse.parseDelimitedFrom(dest)

      assertThat(handlerCalled || cancelCalled).isTrue()
      assertThat(response.requestId).isEqualTo(42)
      if (response.wasCancelled) {
        assertThat(response.getOutput()).isEmpty()
        assertThat(response.exitCode).isEqualTo(0)
      }
      else {
        assertThat(response.getOutput()).startsWith("Such work! Much progress! Wow!")
        assertThat(response.exitCode).isEqualTo(1)
      }

      // checks that nothing more was sent
      assertThat(dest.available()).isEqualTo(0)

      src.close()
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun cancelRequestSendsResponseWhenDone() {
    val waitForCancel = Semaphore(0)
    val handlerCalled = Semaphore(0)
    val cancelCalled = AtomicInteger(0)
    val src = PipedOutputStream()
    val dest = PipedInputStream()
    val failures = ConcurrentLinkedQueue<String>()

    // we force the regular handling to not finish until after we have read the cancel response, to avoid flakiness
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ ->
        // this handler waits until the main thread has sent a cancel request
        handlerCalled.release()
        try {
          waitForCancel.acquire()
        }
        catch (e: InterruptedException) {
          failures.add("Unexpected interrupt waiting for cancel request")
          e.printStackTrace()
        }
        0
      },
      out = PipedOutputStream(dest),
      tracer = OpenTelemetry.noop().getTracer("noop"),
      cancelHandler = { i -> cancelCalled.incrementAndGet() }
    )

    // this thread just makes sure the WorkRequestHandler does work asynchronously
    useHandler(handler, PipedInputStream(src), failures, waitForProcessing = true) {
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      // make sure the handler is called before sending the cancel request, or we might process the cancellation entirely before that
      handlerCalled.acquire()
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)
      waitForCancel.release()

      assertThat(WorkResponse.parseDelimitedFrom(dest).requestId).isEqualTo(42)

      // checks that nothing more was sent
      assertThat(dest.available()).isEqualTo(0)
      src.close()

      // checks that there weren't other unexpected failures
      assertThat(failures).isEmpty()
    }
    assertThat(failures).isEmpty()
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun cancelRequestNoDoubleCancelResponse() {
    val waitForCancel = Semaphore(0)
    val cancelCalled = AtomicInteger(0)
    val src = PipedOutputStream()
    val dest = PipedInputStream()
    val failures = ConcurrentLinkedQueue<String>()

    // we force the regular handling to not finish until after we have read the cancel response, to avoid flakiness
    val inputStream = PipedInputStream(src)
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ ->
        try {
          waitForCancel.acquire()
        }
        catch (e: InterruptedException) {
          failures.add("Unexpected interrupt waiting for cancel request")
          e.printStackTrace()
        }
        0
      },
      out = PipedOutputStream(dest),
      tracer = OpenTelemetry.noop().getTracer("noop"),
      cancelHandler = {
        cancelCalled.incrementAndGet()
      },
    )

    useHandler(handler, inputStream, failures, waitForProcessing = true) {
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)

      waitForCancel.release()

      assertThat(failures).isEmpty()

      val response = WorkResponse.parseDelimitedFrom(dest)
      assertThat(cancelCalled.get()).isLessThan(2)
      assertThat(response.requestId).isEqualTo(42)
      assertThat(response.output).isEmpty()

      // checks that nothing more was sent
      assertThat(dest.available()).isEqualTo(0)
      src.close()
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun cancelRequestSendsNoResponseWhenAlreadySent() {
    val handlerCalled = Semaphore(0)
    val src = PipedOutputStream()
    val dest = PipedInputStream()

    // we force the cancel request to not happen until after we have read the normal response, to avoid flakiness
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { args, err, _, _, _ ->
        handlerCalled.release()
        err.appendLine("Such work! Much progress! Wow!")
        2
      },
      out = PipedOutputStream(dest),
      tracer = OpenTelemetry.noop().getTracer("noop"),
    )

    var r: WorkResponse? = null
    useHandler(handler, PipedInputStream(src), waitForProcessing = true) {
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      r = WorkResponse.parseDelimitedFrom(dest)
      WorkerProtocol.WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)
      src.close()
    }

    val response = r!!
    assertThat(response).isNotNull()
    assertThat(handlerCalled.availablePermits()).isEqualTo(1)
    assertThat(response.requestId).isEqualTo(42)
    assertThat(response.wasCancelled).isFalse()
    assertThat(response.exitCode).isEqualTo(2)
    assertThat(response.getOutput()).startsWith("Such work! Much progress! Wow!")

    // checks that nothing more was sent
    assertThat(dest.available()).isEqualTo(0)
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  fun workRequestHandlerWithWorkRequestCallback() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler<WorkRequest>(
      requestExecutor = { request, err, _, _, _ -> request.arguments.size },
      //ByteArrayInputStream(ByteArray(0)),
      out,
      tracer = OpenTelemetry.noop().getTracer("noop"),
    )

    val args = listOf("--sources", "B.java")
    val request = newWorkRequest(args, requestId = 0)
    runBlocking {
      handler.handleRequest(request = request, requestState = AtomicReference(WorkRequestState.STARTED), tracingContext = null, parentSpan = null)
    }

    val response = WorkResponse.parseDelimitedFrom(ByteArrayInputStream(out.toByteArray()))
    assertThat(response.requestId).isEqualTo(0)
    assertThat(response.exitCode).isEqualTo(2)
    assertThat(response.getOutput()).isEmpty()
  }
}

private fun newWorkRequest(args: List<String>, requestId: Int = 42): WorkRequest {
  return WorkRequest(
    arguments = args.toTypedArray(),
    requestId = requestId,
    inputPaths = emptyArray(),
    //inputDigests = emptyArray(),
    cancel = false,
    verbosity = 0,
    sandboxDir = null
  )
}

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private inline fun useHandler(
  handler: WorkRequestHandler<WorkRequest>,
  input: InputStream,
  failures: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue<String>(),
  waitForProcessing: Boolean = false,
  crossinline errorFilter: (Throwable) -> Boolean = { true },
  task: () -> Unit
) {
  val processor = GlobalScope.async(Dispatchers.Default) {
    try {
      handler.processRequests(WorkRequestReaderWithoutDigest(input), Context.current())
    }
    catch (_: CancellationException) {
    }
    catch (e: Throwable) {
      if (errorFilter(e)) {
        failures.add(e.message!!)
      }
    }
  }

  try {
    task()

    if (waitForProcessing) {
      runBlocking {
        processor.await()
      }
    }

    // checks that there weren't other unexpected failures
    assertThat(failures).isEmpty()
  }
  finally {
    runBlocking {
      if (processor.isCompleted) {
        processor.getCompletionExceptionOrNull()?.let { throw it }
      }
      processor.cancel()
    }
  }
}