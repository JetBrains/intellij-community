// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BlockingLazyTest {
  @Test
  fun computesValueOnceForConcurrentGetters() {
    val invocationCount = AtomicInteger()
    val lazyValue = blockingLazy("test value") {
      invocationCount.incrementAndGet()
      Thread.sleep(20)
      42
    }

    val values = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
      List(8) {
        executor.submit(Callable { lazyValue.get() })
      }.map { it.get(5, TimeUnit.SECONDS) }
    }

    assertThat(values).containsOnly(42)
    assertThat(invocationCount.get()).isEqualTo(1)
  }

  @Test
  fun reusesInitializerFailure() {
    val invocationCount = AtomicInteger()
    val lazyValue = blockingLazy<Int>("failing value") {
      invocationCount.incrementAndGet()
      error("boom")
    }

    repeat(2) {
      assertThatThrownBy {
        lazyValue.get()
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("boom")
    }
    assertThat(invocationCount.get()).isEqualTo(1)
  }

  @Test
  fun failsFastOnRecursiveGet() {
    lateinit var lazyValue: BlockingLazy<Int>
    lazyValue = blockingLazy("recursive value") {
      lazyValue.get()
    }

    assertThatThrownBy {
      lazyValue.get()
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Recursive await")
  }

  /** The initializer runs on a thread of its own, so the lazy must pass the telemetry context of its first caller along. */
  @Test
  fun initializerSeesTheSpanOfTheFirstGetter() {
    val tracer = SdkTracerProvider.builder().build().get("BlockingLazyTest")
    val lazyValue = blockingLazy("traced value") {
      val child = tracer.spanBuilder("child").startSpan()
      child.end()
      (child as ReadableSpan).parentSpanContext
    }

    val parent = tracer.spanBuilder("parent").startSpan()
    val seenParentContext = try {
      parent.makeCurrent().use {
        lazyValue.get()
      }
    }
    finally {
      parent.end()
    }

    assertThat(parent.spanContext.isValid).isTrue()
    assertThat(seenParentContext).isEqualTo(parent.spanContext)
  }
}
