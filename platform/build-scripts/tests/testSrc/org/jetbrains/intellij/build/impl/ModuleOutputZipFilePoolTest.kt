// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class ModuleOutputZipFilePoolTest {
  @Test
  fun `cached lookup times out with clear error`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        val pool = ModuleOutputZipFilePool(
          scope = this,
          cacheReadTimeout = 100.milliseconds,
          zipFileLoader = {
            CompletableDeferred<ZipFile?>().await()
          },
        )

        withTimeout(5.seconds) {
          pool.getData(file, entryPath)
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Timed out after 100ms")
      .hasMessageContaining(entryPath)
      .hasMessageContaining(file.toString())
      // The coroutine dump goes to stderr. A message that carries it is truncated by the build server,
      // and the truncation deletes the stack trace.
      .hasMessageNotContaining("StandaloneCoroutine")
      .hasMessageNotContaining("DeferredCoroutine")
  }

  @Test
  fun `cached lookup loads on a virtual thread and not on the dispatcher of the caller`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val expectedData = "<idea-plugin/>".encodeToByteArray()

    runBlocking(Dispatchers.Default) {
      val loaderInterceptor = CompletableDeferred<ContinuationInterceptor?>()
      val loaderThreadIsVirtual = CompletableDeferred<Boolean>()
      val pool = ModuleOutputZipFilePool(
        scope = this,
        zipFileLoader = {
          loaderInterceptor.complete(currentCoroutineContext()[ContinuationInterceptor])
          loaderThreadIsVirtual.complete(Thread.currentThread().isVirtual)
          zipFile(mapOf(entryPath to expectedData))
        },
      )

      assertThat(pool.getData(file, entryPath)).isEqualTo(expectedData)
      assertThat(loaderInterceptor.await()).isNotSameAs(Dispatchers.Default)
      assertThat(loaderThreadIsVirtual.await()).isTrue()
    }
  }

  @Test
  fun `uncached lookup loads on the IO dispatcher`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val expectedData = "<idea-plugin/>".encodeToByteArray()

    runBlocking(Dispatchers.Default) {
      val loaderInterceptor = CompletableDeferred<ContinuationInterceptor?>()
      val pool = ModuleOutputZipFilePool(
        scope = null,
        zipFileLoader = {
          loaderInterceptor.complete(currentCoroutineContext()[ContinuationInterceptor])
          zipFile(mapOf(entryPath to expectedData))
        },
      )

      assertThat(pool.getData(file, entryPath)).isEqualTo(expectedData)
      assertThat(loaderInterceptor.await()).isSameAs(Dispatchers.IO)
    }
  }

  @Test
  fun `cached lookup rethrows cancellation`() {
    val file = Path.of("module-output.zip")

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        val pool = ModuleOutputZipFilePool(
          scope = this,
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
  fun `timeout of one reader does not cancel the shared load`() {
    val file = Path.of("module-output.zip")
    val entryPath = "META-INF/plugin.xml"
    val expectedData = "<idea-plugin/>".encodeToByteArray()

    runBlocking(Dispatchers.Default) {
      val started = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()
      val loadCount = AtomicInteger(0)
      val pool = ModuleOutputZipFilePool(
        scope = this,
        cacheReadTimeout = 100.milliseconds,
        zipFileLoader = {
          loadCount.incrementAndGet()
          started.complete(Unit)
          release.await()
          zipFile(mapOf(entryPath to expectedData))
        },
      )

      supervisorScope {
        val firstAttempt = async {
          pool.getData(file, entryPath)
        }

        withTimeout(5.seconds) {
          started.await()
        }

        var failure: Throwable? = null
        try {
          firstAttempt.await()
        }
        catch (t: Throwable) {
          failure = t
        }

        assertThat(failure)
          .isInstanceOf(IllegalStateException::class.java)
          .hasMessageContaining("Timed out after 100ms")
      }

      release.complete(Unit)

      assertThat(
        withTimeout(5.seconds) {
          pool.getData(file, entryPath)
        }
      ).isEqualTo(expectedData)
      assertThat(loadCount.get()).isEqualTo(1)
    }
  }

  private fun zipFile(entries: Map<String, ByteArray>): ZipFile {
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

      override fun close() = Unit
    }
  }
}
