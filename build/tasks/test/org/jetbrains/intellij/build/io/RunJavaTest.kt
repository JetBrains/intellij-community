package org.jetbrains.intellij.build.io

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfoRt
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Timeout(30)
class RunJavaTest {
  @TempDir
  lateinit var tempDir: Path

  @RepeatedTest(5)
  fun `an interrupt waits for Java termination and preserves interruption`() {
    val pidFile = tempDir.resolve("child.pid")
    val failure = CompletableFuture<Throwable?>()
    val interrupted = AtomicBoolean()
    val runner = Thread.ofVirtual().start {
      try {
        runChild(pidFile, "sleep")
        failure.complete(null)
      }
      catch (error: Throwable) {
        interrupted.set(Thread.currentThread().isInterrupted)
        failure.complete(error)
      }
    }
    try {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (!Files.exists(pidFile) || Files.size(pidFile) == 0L) {
        check(!failure.isDone) { "The child failed to start: ${failure.get()}" }
        check(System.nanoTime() < deadline) { "The child did not start" }
        Thread.sleep(20)
      }
      val process = ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElseThrow()
      runner.interrupt()
      assertThat(failure.get(5, TimeUnit.SECONDS)).isInstanceOf(InterruptedException::class.java)
      assertThat(interrupted.get()).isTrue()
      assertThat(process.isAlive).isFalse()
    }
    finally {
      runner.interrupt()
      runner.join(5000)
      assertThat(runner.isAlive).isFalse()
    }
  }

  @Test
  fun `a timeout reports the output after Java terminates`() {
    val pidFile = tempDir.resolve("child.pid")
    assertThatThrownBy {
      runChild(pidFile, "sleep", timeout = 2.seconds)
    }.isInstanceOf(RuntimeException::class.java)
      .hasMessageContaining("Timed out waiting for")
      .hasMessageContaining("child output")
    val pid = Files.readString(pidFile).trim().toLong()
    assertThat(ProcessHandle.of(pid).filter { it.isAlive }).isEmpty()
  }

  @Test
  fun `custom output creates the parent directory and remains readable`() {
    val output = tempDir.resolve("logs/output.txt")
    @Suppress("IO_FILE_USAGE")
    val redirect = Redirect.to(output.toFile())
    runChild(tempDir.resolve("child.pid"), "exit", customOutput = redirect)
    assertThat(Files.readString(output)).isEqualTo("child output")
  }

  private fun runChild(
    pidFile: Path,
    action: String,
    timeout: Duration = 10.seconds,
    customOutput: Redirect? = null,
  ) {
    val javaName = if (SystemInfoRt.isWindows) "java.exe" else "java"
    runJava(
      mainClass = RunJavaChild::class.java.name,
      args = listOf(pidFile.toString(), action),
      classPath = listOf(RunJavaChild::class.java, Unit::class.java).map { childClass ->
        requireNotNull(PathManager.getJarForClass(childClass)).toString()
      },
      javaExe = Path.of(System.getProperty("java.home"), "bin", javaName),
      timeout = timeout,
      customOutput = customOutput,
    )
  }
}

internal object RunJavaChild {
  @JvmStatic
  fun main(args: Array<String>) {
    Files.writeString(Path.of(args[0]), ProcessHandle.current().pid().toString())
    print("child output")
    if (args[1] == "sleep") {
      Thread.sleep(60_000)
    }
  }
}
