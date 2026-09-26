// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.ZipFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildLifetime
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.time.Duration.Companion.milliseconds

internal class ModuleOutputZipFilePoolTest {
  @Test
  fun `cached lookup times out with clear error`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val release = CountDownLatch(1)

    try {
      assertThatThrownBy {
        BuildLifetime().use { lifetime ->
          val pool = ModuleOutputZipFilePool(
            lifetime = lifetime,
            cacheReadTimeout = 100.milliseconds,
            zipFileLoader = {
              release.await()
              null
            },
          )

          pool.getData(file, entryPath)
        }
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Timed out after 100ms")
        .hasMessageContaining(entryPath)
        .hasMessageContaining(file.toString())
        // The thread dump goes to stderr. A message that carries it is truncated by the build server,
        // and the truncation deletes the stack trace.
        .hasMessageNotContaining("AsyncCache: ")
        .hasMessageNotContaining("at java.")
    }
    finally {
      release.countDown()
    }
  }

  @Test
  fun `cached lookup loads on a thread of its own`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val expectedData = "<idea-plugin/>".encodeToByteArray()

    BuildLifetime().use { lifetime ->
      val callerThread = Thread.currentThread()
      val loaderThread = arrayOfNulls<Thread>(1)
      val pool = ModuleOutputZipFilePool(
        lifetime = lifetime,
        zipFileLoader = {
          loaderThread[0] = Thread.currentThread()
          zipFile(mapOf(entryPath to expectedData))
        },
      )

      assertThat(pool.getData(file, entryPath)).isEqualTo(expectedData)
      val loader = loaderThread[0]!!
      assertThat(loader).isNotSameAs(callerThread)
      assertThat(loader.isVirtual).isTrue()
      assertThat(loader.name).startsWith("build: ")
    }
  }

  @Test
  fun `uncached lookup loads on the thread of the caller`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val expectedData = "<idea-plugin/>".encodeToByteArray()

    val loaderThread = arrayOfNulls<Thread>(1)
    val pool = ModuleOutputZipFilePool(
      lifetime = null,
      zipFileLoader = {
        loaderThread[0] = Thread.currentThread()
        zipFile(mapOf(entryPath to expectedData))
      },
    )

    assertThat(pool.getData(file, entryPath)).isEqualTo(expectedData)
    assertThat(loaderThread[0]).isSameAs(Thread.currentThread())
  }

  @Test
  fun `cached lookup rethrows cancellation`() {
    val file = Path.of("module-output.zip")

    assertThatThrownBy {
      BuildLifetime().use { lifetime ->
        val pool = ModuleOutputZipFilePool(
          lifetime = lifetime,
          zipFileLoader = {
            throw CancellationException("stop")
          },
        )

        pool.getData(file, "META-INF/plugin.xml")
      }
    }
      .isInstanceOf(CancellationException::class.java)
      .hasMessage("stop")
  }

  @Test
  fun `the lifetime closes the cached files`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val closed = AtomicInteger()

    BuildLifetime().use { lifetime ->
      val pool = ModuleOutputZipFilePool(
        lifetime = lifetime,
        zipFileLoader = { zipFile(mapOf(entryPath to "<idea-plugin/>".encodeToByteArray()), onClose = { closed.incrementAndGet() }) },
      )
      pool.getData(file, entryPath)
      assertThat(closed.get()).isEqualTo(0)
    }

    assertThat(closed.get()).isEqualTo(1)
  }

  @Test
  fun `timeout of one reader does not cancel the shared load`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val expectedData = "<idea-plugin/>".encodeToByteArray()

    BuildLifetime().use { lifetime ->
      val started = CountDownLatch(1)
      val release = CountDownLatch(1)
      val loadCount = AtomicInteger(0)
      val pool = ModuleOutputZipFilePool(
        lifetime = lifetime,
        cacheReadTimeout = 100.milliseconds,
        zipFileLoader = {
          loadCount.incrementAndGet()
          started.countDown()
          release.await()
          zipFile(mapOf(entryPath to expectedData))
        },
      )

      val failure = arrayOfNulls<Throwable>(1)
      val firstAttempt = Thread.ofVirtual().start {
        try {
          pool.getData(file, entryPath)
        }
        catch (t: Throwable) {
          failure[0] = t
        }
      }

      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
      firstAttempt.join()

      assertThat(failure[0])
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Timed out after 100ms")

      release.countDown()

      assertThat(pool.getData(file, entryPath)).isEqualTo(expectedData)
      assertThat(loadCount.get()).isEqualTo(1)
    }
  }

  private fun zipFile(entries: Map<String, ByteArray>, onClose: () -> Unit = {}): ZipFile {
    return object : ZipFile {
      override fun getInputStream(path: String): InputStream? = entries[path]?.inputStream()

      override fun getByteBuffer(path: String): ByteBuffer? = entries[path]?.let { ByteBuffer.wrap(it) }

      override fun getData(name: String): ByteArray? = entries[name]

      override fun getResource(name: String): ZipFile.ZipResource? = null

      override fun processResources(
        dir: String,
        nameFilter: Predicate<in String>,
        consumer: BiConsumer<in String, in InputStream>,
      ) = Unit

      override fun close() = onClose()
    }
  }
}
