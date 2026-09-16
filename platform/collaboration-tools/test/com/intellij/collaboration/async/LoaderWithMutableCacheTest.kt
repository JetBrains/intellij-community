// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LoaderWithMutableCacheTest {

  @Test
  fun `load returns the loaded value and caches it`() = timeoutRunBlockingWithBackgroundScope { bg ->
    var loads = 0
    val loader = LoaderWithMutableCache(bg) { loads++; "value-$loads" }

    assertEquals("value-1", loader.load())
    assertEquals("value-1", loader.load()) // served from cache
    assertEquals(1, loads)
  }

  @Test
  fun `clearCache makes the next load recompute`() = timeoutRunBlockingWithBackgroundScope { bg ->
    var loads = 0
    val loader = LoaderWithMutableCache(bg) { loads++; "value-$loads" }

    assertEquals("value-1", loader.load())
    loader.clearCache()
    assertEquals("value-2", loader.load())
    assertEquals(2, loads)
  }

  @Test
  fun `overrideResult publishes the value without invoking the loader`() = timeoutRunBlockingWithBackgroundScope { bg ->
    var loads = 0
    val loader = LoaderWithMutableCache(bg) { loads++; "loaded" }

    loader.overrideResult("overridden")
    assertEquals("overridden", loader.load())
    assertEquals(0, loads)
  }

  @Test
  fun `updateLoaded transforms the cached value`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = LoaderWithMutableCache(bg) { listOf(1, 2) }

    assertEquals(listOf(1, 2), loader.load())
    loader.updateLoaded { it + 3 }
    assertEquals(listOf(1, 2, 3), loader.load())
  }

  @Test
  fun `updateLoaded is a no-op before anything is loaded`() = timeoutRunBlockingWithBackgroundScope { bg ->
    var loads = 0
    val loader = LoaderWithMutableCache(bg) { loads++; listOf("loaded") }

    loader.updateLoaded { it + "extra" } // nothing loaded yet, so the update is dropped
    assertEquals(listOf("loaded"), loader.load())
    assertEquals(1, loads)
  }

  @Test
  fun `updateLoaded with an unchanged value does nothing`() = timeoutRunBlockingWithBackgroundScope { bg ->
    var loads = 0
    val loader = LoaderWithMutableCache(bg) { loads++; listOf(1, 2) }
    assertEquals(listOf(1, 2), loader.load())

    loader.updatedSignal.test {
      loader.updateLoaded { it } // equal value -> no override
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(listOf(1, 2), loader.load())
    assertEquals(1, loads) // loader not re-invoked
  }

  @Test
  fun `updatedSignal fires on invalidation and mutation but not on the initial load`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = LoaderWithMutableCache(bg) { 0 }

    loader.updatedSignal.test {
      loader.load() // the initial computation is not a "change"
      expectNoEvents()

      loader.clearCache()
      awaitItem()

      loader.overrideResult(1)
      awaitItem()

      loader.updateLoaded { it + 1 }
      awaitItem()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `concurrent loads share a single computation`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val gate = CompletableDeferred<Unit>()
    var loads = 0
    val loader = LoaderWithMutableCache(bg) {
      loads++
      gate.await()
      "value"
    }

    val first = async { loader.load() }
    val second = async { loader.load() }
    gate.complete(Unit)

    assertEquals("value", first.await())
    assertEquals("value", second.await())
    assertEquals(1, loads)
  }

  @Test
  fun `load rethrows the loader failure and clearCache allows a retry`() = timeoutRunBlockingWithBackgroundScope { bg ->
    var attempt = 0
    val loader = LoaderWithMutableCache(bg) {
      attempt++
      if (attempt == 1) error("boom") else "ok"
    }

    val failure = runCatching { loader.load() }.exceptionOrNull()
    assertTrue(failure is IllegalStateException && failure.message == "boom")

    loader.clearCache()
    assertEquals("ok", loader.load())
  }
}
