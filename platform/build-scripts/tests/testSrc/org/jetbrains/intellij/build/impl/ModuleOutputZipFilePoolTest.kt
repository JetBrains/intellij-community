// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path
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
      .hasMessageContaining("possible deadlock")
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
}
