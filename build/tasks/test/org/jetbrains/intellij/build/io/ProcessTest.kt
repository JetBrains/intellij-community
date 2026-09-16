// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.SystemInfoRt
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(30)
class ProcessTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun assumeShell() {
      assumeTrue(SystemInfoRt.isUnix)
      assumeTrue {
        runCatching {
          runProcess(args = listOf("sh", "-c", "exit 0"))
        }.isSuccess
      }
    }
  }

  private fun runShell(
    code: String,
    timeout: Duration,
    stdOutConsumer: (String) -> Unit = {},
    stdErrConsumer: (String) -> Unit = {},
  ) {
    val script = Files.createTempFile("script", ".sh").toFile()
    try {
      script.writeText(code)
      assertThat(script.setExecutable(true)).isTrue()
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(script.toPath(), PosixFilePermission.entries.toSet())
      }
      runProcess(args = listOf("sh", script.absolutePath), timeout = timeout,
                 stdOutConsumer = stdOutConsumer, stdErrConsumer = stdErrConsumer)
    }
    finally {
      Files.deleteIfExists(script.toPath())
    }
  }

  @Test
  fun success() {
    runShell(code = "sleep 1", timeout = DEFAULT_TIMEOUT)
  }

  @Test
  fun `the output reaches the consumer line by line`() {
    val lines = ArrayList<String>()
    runShell(code = "echo one; echo two", timeout = DEFAULT_TIMEOUT, stdOutConsumer = lines::add)
    assertThat(lines).containsExactly("one", "two")
  }

  @Test
  fun `the reader preserves line endings and partial UTF-8 characters`() {
    val lines = ArrayList<String>()
    val prefix = "x".repeat(8191)
    runShell(
      code = "printf '\\r\\nfirst\\rsecond\\n\\n'; printf '${prefix}\\342'; sleep 1; printf '\\202\\254\\nlast'",
      timeout = DEFAULT_TIMEOUT,
      stdOutConsumer = lines::add,
    )
    assertThat(lines).containsExactly("", "first", "second", "", "$prefix€", "last")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a consumer IOException stops the process and propagates unchanged`(stderr: Boolean) {
    val failure = IOException("the consumer failed")
    val processHandle = CompletableFuture<ProcessHandle>()
    val consumer: (String) -> Unit = { line ->
      processHandle.complete(ProcessHandle.of(line.toLong()).orElseThrow())
      throw failure
    }
    assertThatThrownBy {
      runShell(
        code = "echo $$${if (stderr) " >&2" else ""}; exec sleep 60",
        timeout = 10.seconds,
        stdOutConsumer = if (stderr) ({}) else consumer,
        stdErrConsumer = if (stderr) consumer else ({}),
      )
    }.isSameAs(failure)
    assertThat(processHandle.get(5, TimeUnit.SECONDS).isAlive).isFalse()
  }

  @Test
  fun `a timeout joins both consumers before returning`() {
    val started = CountDownLatch(2)
    val release = CountDownLatch(1)
    val consumers = ConcurrentLinkedQueue<Thread>()
    val finished = AtomicInteger()
    val consumer: (String) -> Unit = {
      consumers.add(Thread.currentThread())
      started.countDown()
      try {
        release.await()
      }
      finally {
        finished.incrementAndGet()
      }
    }
    try {
      assertThatThrownBy {
        runShell(
          code = "echo output; echo error >&2; exec sleep 60",
          timeout = 2.seconds,
          stdOutConsumer = consumer,
          stdErrConsumer = consumer,
        )
      }.isInstanceOf(TimeoutException::class.java)
      assertThat(started.count).isZero()
      assertThat(finished.get()).isEqualTo(2)
      assertThat(consumers).allMatch { !it.isAlive }
    }
    finally {
      release.countDown()
    }
  }

  @RepeatedTest(5)
  fun `a consumer that clears interruption does not receive another line`() {
    val calls = AtomicInteger()
    val release = CountDownLatch(1)
    try {
      assertThatThrownBy {
        runShell(code = "echo one; echo two", timeout = 1.seconds, stdOutConsumer = {
          calls.incrementAndGet()
          try {
            release.await(5, TimeUnit.SECONDS)
          }
          catch (_: InterruptedException) {
          }
        })
      }.isInstanceOf(TimeoutException::class.java)
      assertThat(calls.get()).isEqualTo(1)
    }
    finally {
      release.countDown()
    }
  }

  @Test
  fun `an interrupt during output draining joins the consumer`() {
    val processHandle = CompletableFuture<ProcessHandle?>()
    val consumerThread = CompletableFuture<Thread>()
    val release = CountDownLatch(1)
    val failure = CompletableFuture<Throwable?>()
    val interrupted = AtomicBoolean()
    val runner = Thread.ofVirtual().start {
      try {
        runShell(code = "echo $$", timeout = 10.seconds, stdOutConsumer = { line ->
          consumerThread.complete(Thread.currentThread())
          processHandle.complete(ProcessHandle.of(line.toLong()).orElse(null))
          release.await()
        })
        failure.complete(null)
      }
      catch (error: Throwable) {
        interrupted.set(Thread.currentThread().isInterrupted)
        failure.complete(error)
      }
    }
    try {
      processHandle.get(5, TimeUnit.SECONDS)?.onExit()?.get(5, TimeUnit.SECONDS)
      runner.interrupt()
      assertThat(failure.get(5, TimeUnit.SECONDS)).isInstanceOf(InterruptedException::class.java)
      assertThat(interrupted.get()).isTrue()
      assertThat(consumerThread.get(5, TimeUnit.SECONDS).isAlive).isFalse()
    }
    finally {
      release.countDown()
      runner.interrupt()
      runner.join(5000)
      assertThat(runner.isAlive).isFalse()
    }
  }

  @Test
  fun `a consumer failure remains primary when another consumer fails during cleanup`() {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val primary = IOException("the stderr consumer failed")
    val cleanup = IllegalStateException("the stdout consumer failed during cleanup")
    try {
      assertThatThrownBy {
        runShell(
          code = "echo output; echo error >&2",
          timeout = 10.seconds,
          stdOutConsumer = {
            started.countDown()
            try {
              release.await()
            }
            catch (_: InterruptedException) {
              throw cleanup
            }
          },
          stdErrConsumer = {
            check(started.await(5, TimeUnit.SECONDS))
            throw primary
          },
        )
      }.isSameAs(primary)
      assertThat(primary.suppressed).containsExactly(cleanup)
      assertThat(cleanup.suppressed).isEmpty()
    }
    finally {
      release.countDown()
    }
  }

  @Test
  fun `a descendant with an open pipe does not delay completion`() {
    val pidFile = Files.createTempFile("descendant", ".pid")
    val lines = ArrayList<String>()
    try {
      runShell(
        code = "sleep 60 & echo $! > '$pidFile'; echo ready; printf tail",
        timeout = 2.seconds,
        stdOutConsumer = lines::add,
      )
      assertThat(lines).containsExactly("ready", "tail")
    }
    finally {
      Files.readString(pidFile).trim().toLongOrNull()?.let { pid ->
        ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
      }
      Files.deleteIfExists(pidFile)
    }
  }

  @Test
  fun `both consumers inherit the telemetry context and process span`() {
    val key = ContextKey.named<String>("test flow")
    val stdout = CompletableFuture<Context>()
    val stderr = CompletableFuture<Context>()
    SdkTracerProvider.builder().build().use { provider ->
      val tracer = provider.get("process test")
      TraceManager.pushTracer(tracer).use {
        val parent = tracer.spanBuilder("parent").startSpan()
        try {
          Context.current().with(parent).with(key, "flow").makeCurrent().use {
            runShell(
              code = "echo output; echo error >&2",
              timeout = DEFAULT_TIMEOUT,
              stdOutConsumer = { stdout.complete(Context.current()) },
              stdErrConsumer = { stderr.complete(Context.current()) },
            )
          }
          val outputContext = stdout.get(5, TimeUnit.SECONDS)
          val errorContext = stderr.get(5, TimeUnit.SECONDS)
          for (context in listOf(outputContext, errorContext)) {
            assertThat(context.get(key)).isEqualTo("flow")
            val span = Span.fromContext(context).spanContext
            assertThat(span.isValid).isTrue()
            assertThat(span.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(span.spanId).isNotEqualTo(parent.spanContext.spanId)
          }
          assertThat(Span.fromContext(outputContext)).isSameAs(Span.fromContext(errorContext))
        }
        finally {
          parent.end()
        }
      }
    }
  }

  @Test
  fun timeout() {
    assertThatThrownBy {
      runShell(code = "sleep 1", timeout = 10.milliseconds)
    }.isInstanceOf(TimeoutException::class.java)
  }

  @Test
  fun `a failure message carries a bounded output tail`() {
    assertThatThrownBy {
      runShell(code = "seq 1 150 >&2; exit 3", timeout = DEFAULT_TIMEOUT)
    }
      .hasMessageContaining("exitCode 3")
      .hasMessageContaining("[50 earlier output lines omitted]\n51\n")
      .hasMessageEndingWith("\n150")
  }

  /** The process runner is a blocking body of a fork, so an interrupt of the calling thread must not leave the child running. */
  @Test
  fun `an interrupt kills the process`() {
    val pidFile = Files.createTempFile("pid", ".txt")
    val failure = CompletableFuture<Throwable>()
    val runner = Thread.ofVirtual().start {
      try {
        runShell(code = "echo $$ > $pidFile; exec sleep 60", timeout = DEFAULT_TIMEOUT)
        failure.complete(null)
      }
      catch (e: Throwable) {
        failure.complete(e)
      }
    }
    try {
      waitForPidFile(pidFile)
      runner.interrupt()
      assertThat(failure.orTimeout(10, TimeUnit.SECONDS).join()).isInstanceOf(InterruptedException::class.java)
      val pid = Files.readString(pidFile).trim().toLong()
      assertThat(ProcessHandle.of(pid).filter { it.isAlive }).isEmpty()
    }
    finally {
      runner.interrupt()
      runner.join(5000)
      assertThat(runner.isAlive).isFalse()
      Files.deleteIfExists(pidFile)
    }
  }

  private fun waitForPidFile(pidFile: Path) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (Files.size(pidFile) == 0L) {
      check(System.nanoTime() < deadline) { "the script did not start" }
      Thread.sleep(20)
    }
  }

  @Test
  fun threadDump() {
    dumpThreads(pid = ProcessHandle.current().pid(), javaExe = currentJavaExe())
  }

  /** The `java` of the JVM that runs the test. Its sibling `jstack` is the first candidate of [dumpThreads]. */
  private fun currentJavaExe(): Path {
    val name = if (SystemInfoRt.isWindows) "java.exe" else "java"
    return Path.of(System.getProperty("java.home"), "bin", name)
  }
}
