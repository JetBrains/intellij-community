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

import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.*
import java.lang.Thread.UncaughtExceptionHandler
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the WorkRequestHandler
 */
//@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WorkRequestHandlerTest {
  companion object {
    private fun createTestWorkerIo(): WorkerIo {
      val captured = ByteArrayOutputStream()
      return WorkerIo(
        originalInputStream = System.`in`,
        originalOutputStream = System.out,
        originalErrorStream = System.err,
        capturedStream = captured,
        restore = captured
      )
    }
  }

  private val testWorkerIo = createTestWorkerIo()

  @AfterEach
  fun after() {
    testWorkerIo.close()
  }

  @Test
  fun normalWorkRequest() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler(
      executor = { args, err, _ -> 1 },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(input = ByteArrayInputStream(ByteArray(0)), output = out),
    )

    val request = WorkRequest.newBuilder().addAllArguments(listOf("--sources", "A.java")).build()
    handler.handleRequest(workerIo = testWorkerIo, request = request, requestState = AtomicReference(Thread.currentThread()))

    val response = WorkResponse.parseDelimitedFrom(out.toByteArray().inputStream())
    assertThat(response.requestId).isEqualTo(0)
    assertThat(response.exitCode).isEqualTo(1)
    assertThat(response.getOutput()).isEmpty()
  }

  @Test
  fun multiplexWorkRequest() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler(
      executor = { args, err, _ -> 0 },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(ByteArray(0).inputStream(), out)
    )

    val request = newWorkRequest(listOf("--sources", "A.java"))
    handler.handleRequest(workerIo = testWorkerIo, request = request, requestState = AtomicReference(Thread.currentThread()))

    val response = WorkResponse.parseDelimitedFrom(out.toByteArray().inputStream())
    assertThat(response.requestId).isEqualTo(42)
    assertThat(response.exitCode).isEqualTo(0)
    assertThat(response.getOutput()).isEmpty()
  }

  @Test
  fun multiplexWorkRequestStopsThreadsOnShutdown() {
    val src = PipedOutputStream()
    val dest = PipedInputStream()

    // work request threads release this when they have started
    val started = Semaphore(0)
    // work request threads wait forever on this, so we can see how they react to closed stdin
    val eternity = Semaphore(0)
    // released when the work request handler thread has noticed the closed stdin and interrupted the work request threads
    val workerThreads = ArrayList<WeakReference<Thread>>()
    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
        // each call to this, runs in its own thread
        synchronized(workerThreads) {
          workerThreads.add(WeakReference(Thread.currentThread()))
        }
        started.release()
        // this blocks forever
        eternity.acquire()
        0
      },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(input = PipedInputStream(src), output = PipedOutputStream(dest))
    )

    useHandler(handler = handler, failures = ConcurrentLinkedQueue()) {
      val args = listOf("--sources", "A.java")
      val request1 = newWorkRequest(args)
      request1.writeDelimitedTo(src)
      val request2 = WorkRequest.newBuilder().addAllArguments(args).setRequestId(43).build()
      request2.writeDelimitedTo(src)

      // wait
      started.acquire(2)
      assertThat(workerThreads).hasSize(2)
      // now both request threads are started, closing the input to the "worker" should shut it down
      src.close()

      waitForDie(workerThreads)
    }
  }

  private fun waitForDie(workerThreads: Collection<WeakReference<Thread>>) {
    repeat(10) {
      if (workerThreads.all {
          val thread = it.get()
          thread == null || !thread.isAlive
        }) {
        return
      }

      Thread.sleep(1.seconds.inWholeMilliseconds)
    }

    throw AssertionError("All worker threads should have stopped: " + workerThreads.joinToString { it.get()?.toString() ?: "null" })
  }

  @Test
  fun multiplexWorkRequestStopsWorkerOnException() {
    val src = PipedOutputStream()
    val dest = PipedInputStream()

    // work request threads release this when they have started
    val started = Semaphore(0)
    // one work request threads waits forever on this, so the second one can throw an exception
    val eternity = Semaphore(0)
    val workerThreads = ConcurrentLinkedQueue<WeakReference<Thread>>()
    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
        // each call to this, runs in its own thread
        try {
          synchronized(workerThreads) {
            workerThreads.add(WeakReference(Thread.currentThread()))
          }
          started.release()
          if (workerThreads.size < 2) {
            // this blocks forever
            eternity.acquire()
          }
          else {
            throw Error("Intentional death!")
          }
        }
        catch (e: InterruptedException) {
          throw AssertionError("Unhandled exception", e)
        }
        0
      },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(input = PipedInputStream(src), output = PipedOutputStream(dest)),
    )

    useHandler(handler = handler, failures = ConcurrentLinkedQueue()) {
      val args = listOf("--sources", "A.java")
      newWorkRequest(args).writeDelimitedTo(src)
      newWorkRequest(args, 43).writeDelimitedTo(src)

      started.acquire(2)
      assertThat(workerThreads).hasSize(2)
      waitForDie(workerThreads)
    }
  }

  @Test
  fun testOutput() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
        err.appendLine("Failed!")
        1
      },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(ByteArray(0).inputStream(), out)
    )

    val args = listOf("--sources", "A.java")
    val request = WorkRequest.newBuilder().addAllArguments(args).build()
    handler.handleRequest(testWorkerIo, request, AtomicReference(Thread.currentThread()))

    val response = WorkResponse.parseDelimitedFrom(out.toByteArray().inputStream())
    assertThat(response.requestId).isEqualTo(0)
    assertThat(response.exitCode).isEqualTo(1)
    assertThat(response.getOutput()).contains("Failed!")
  }

  @Test
  fun testException() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
        throw RuntimeException("Exploded!")
      },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(ByteArray(0).inputStream(), out)
    )

    val args = listOf("--sources", "A.java")
    val request = WorkRequest.newBuilder().addAllArguments(args).build()
    handler.handleRequest(workerIo = testWorkerIo, request = request, requestState = AtomicReference(Thread.currentThread()))

    val response = WorkResponse.parseDelimitedFrom(ByteArrayInputStream(out.toByteArray()))
    assertThat(response.requestId).isEqualTo(0)
    assertThat(response.exitCode).isEqualTo(1)
    assertThat(response.getOutput()).startsWith("java.lang.RuntimeException: Exploded!")
  }

  @Test
  fun cancelRequestExactlyOneResponseSent() {
    var handlerCalled = false
    var cancelCalled = false
    val src = PipedOutputStream()
    val dest = PipedInputStream()

    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
        handlerCalled = true
        err.appendLine("Such work! Much progress! Wow!")
        1
      },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(input = PipedInputStream(src), output = PipedOutputStream(dest)),
      cancelHandler = {
        cancelCalled = true
      },
    )

    useHandler(handler) {
      WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)

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
    }
  }

  @Test
  fun cancelRequestSendsResponseWhenDone() {
    val waitForCancel = Semaphore(0)
    val handlerCalled = Semaphore(0)
    val cancelCalled = AtomicInteger(0)
    val src = PipedOutputStream()
    val dest = PipedInputStream()
    val failures = ConcurrentLinkedQueue<String>()

    // we force the regular handling to not finish until after we have read the cancel response, to avoid flakiness
    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
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
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(input = PipedInputStream(src), output = PipedOutputStream(dest)),
      cancelHandler = { i -> cancelCalled.incrementAndGet() }
    )

    // this thread just makes sure the WorkRequestHandler does work asynchronously
    useHandler(handler, failures) {
      WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      // make sure the handler is called before sending the cancel request, or we might process the cancellation entirely before that
      handlerCalled.acquire()
      WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)
      waitForCancel.release()

      assertThat(WorkResponse.parseDelimitedFrom(dest).requestId).isEqualTo(42)

      // checks that nothing more was sent
      assertThat(dest.available()).isEqualTo(0)
      src.close()

      // checks that there weren't other unexpected failures
      assertThat(failures).isEmpty()
      handler.stopAndAwait()
      assertThat(failures).isEmpty()
    }
  }

  @Test
  fun cancelRequestNoDoubleCancelResponse() {
    val waitForCancel = Semaphore(0)
    val cancelCalled = AtomicInteger(0)
    val src = PipedOutputStream()
    val dest = PipedInputStream()
    val failures = ConcurrentLinkedQueue<String>()

    // we force the regular handling to not finish until after we have read the cancel response, to avoid flakiness
    val inputStream = PipedInputStream(src)
    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
        try {
          waitForCancel.acquire()
        }
        catch (e: InterruptedException) {
          failures.add("Unexpected interrupt waiting for cancel request")
          e.printStackTrace()
        }
        0
      },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(input = inputStream, output = PipedOutputStream(dest)),
      cancelHandler = {
        cancelCalled.incrementAndGet()
      },
    )

    useHandler(handler, failures) {
      WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)
      WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)

      waitForCancel.release()

      assertThat(failures).isEmpty()

      val response = WorkResponse.parseDelimitedFrom(dest)
      assertThat(cancelCalled.get()).isLessThan(2)
      assertThat(response.requestId).isEqualTo(42)
      assertThat(response.getOutput()).isEmpty()
      assertThat(response.wasCancelled).isTrue()

      // Checks that nothing more was sent.
      assertThat(dest.available()).isEqualTo(0)
      src.close()
    }
  }

  @Test
  fun cancelRequestSendsNoResponseWhenAlreadySent() {
    val handlerCalled = Semaphore(0)
    val src = PipedOutputStream()
    val dest = PipedInputStream()

    // we force the cancel request to not happen until after we have read the normal response, to avoid flakiness
    val handler = WorkRequestHandler(
      executor = { args, err, _ ->
        handlerCalled.release()
        err.appendLine("Such work! Much progress! Wow!")
        2
      },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(input = PipedInputStream(src), output = PipedOutputStream(dest)),
    )

    useHandler(handler) {
      WorkRequest.newBuilder().setRequestId(42).build().writeDelimitedTo(src)
      val response = WorkResponse.parseDelimitedFrom(dest)
      WorkRequest.newBuilder().setRequestId(42).setCancel(true).build().writeDelimitedTo(src)
      handler.stopAndAwait()

      assertThat(response).isNotNull()

      assertThat(handlerCalled.availablePermits()).isEqualTo(1)
      assertThat(response.requestId).isEqualTo(42)
      assertThat(response.wasCancelled).isFalse()
      assertThat(response.exitCode).isEqualTo(2)
      assertThat(response.getOutput()).startsWith("Such work! Much progress! Wow!")

      // checks that nothing more was sent
      assertThat(dest.available()).isEqualTo(0)
      src.close()
    }
  }

  @Test
  fun workRequestHandlerWithWorkRequestCallback() {
    val out = ByteArrayOutputStream()
    val handler = WorkRequestHandler(
      executor = { request, err, _ -> request.argumentsCount },
      errorStream = PrintStream(ByteArrayOutputStream()),
      messageProcessor = ProtoWorkerMessageProcessor(ByteArrayInputStream(ByteArray(0)), out),
    )

    val args = listOf("--sources", "B.java")
    val request = WorkRequest.newBuilder().addAllArguments(args).build()
    handler.handleRequest(workerIo = testWorkerIo, request = request, requestState = AtomicReference(Thread.currentThread()))

    val response = WorkResponse.parseDelimitedFrom(ByteArrayInputStream(out.toByteArray()))
    assertThat(response.requestId).isEqualTo(0)
    assertThat(response.exitCode).isEqualTo(2)
    assertThat(response.getOutput()).isEmpty()
  }

  @Test
  fun workerIODoesWrapSystemStreams() {
    // Save the original streams
    val originalInputStream = System.`in`
    val originalOutputStream = System.out
    val originalErrorStream = System.err

    // Swap in the test streams to assert against
    val byteArrayInputStream = ByteArray(0).inputStream()
    System.setIn(byteArrayInputStream)
    val outputBuffer = PrintStream(ByteArrayOutputStream(), true)
    System.setOut(outputBuffer)
    System.setErr(outputBuffer)

    try {
      outputBuffer.use {
        byteArrayInputStream.use {
          wrapStandardSystemStreams().use { io ->
            // Assert that the WorkerIO returns the correct wrapped streams and the new System instance
            // has been swapped out with the wrapped one
            assertThat(io.originalInputStream).isSameAs(byteArrayInputStream)
            assertThat(System.`in`).isNotSameAs(byteArrayInputStream)

            assertThat(io.originalOutputStream).isSameAs(outputBuffer)
            assertThat(System.out).isNotSameAs(outputBuffer)

            assertThat(io.originalErrorStream).isSameAs(outputBuffer)
            assertThat(System.err).isNotSameAs(outputBuffer)
          }
        }
      }
    }
    finally {
      // swap back in the original streams
      System.setIn(originalInputStream)
      System.setOut(originalOutputStream)
      System.setErr(originalErrorStream)
    }
  }

  @Test
  fun workerIODoesCaptureStandardOutAndErrorStreams() {
    wrapStandardSystemStreams().use { io ->
      // assert that nothing has been captured in the new instance
      assertThat(io.readCapturedAsUtf8String()).isEmpty()

      // Assert that the standard out/error stream redirect to our own streams
      print("This is a standard out message!")
      System.err.print("This is a standard error message!")
      assertThat(io.readCapturedAsUtf8String()).isEqualTo("This is a standard out message!This is a standard error message!")

      // Assert that readCapturedAsUtf8String calls reset on the captured stream after a read
      assertThat(io.readCapturedAsUtf8String()).isEmpty()

      print("out 1")
      System.err.print("err 1")
      print("out 2")
      System.err.print("err 2")
      assertThat(io.readCapturedAsUtf8String()).isEqualTo("out 1err 1out 2err 2")
      assertThat(io.readCapturedAsUtf8String()).isEmpty()
    }
  }
}

private fun newWorkRequest(args: List<String>, id: Int = 42): WorkRequest {
  return WorkRequest.newBuilder().addAllArguments(args).setRequestId(id).build()
}

private inline fun useHandler(
  handler: WorkRequestHandler,
  failures: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue<String>(),
  task: () -> Unit
) {
  wrapStandardSystemStreams().use { workerIo ->
    val readThread = handler.startRead(workerIo, finishTimeout = 1.seconds)
    readThread.uncaughtExceptionHandler = UncaughtExceptionHandler { thread, e ->
      failures.add(e.message!!)
    }

    try {
      task()

      // checks that there weren't other unexpected failures
      assertThat(failures).isEmpty()
      handler.stopAndAwait()
      assertThat(failures).isEmpty()
    }
    finally {
      handler.stopNow()
      readThread.interrupt()
    }
  }
}