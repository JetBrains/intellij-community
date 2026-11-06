// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.diagnostic.ProgressIndicatorDumper
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@TestApplication
internal class CoreProgressManagerTopLevelIndicatorTest {
  /** A manager whose [getProgressIndicator] never returns `null`, like the language server's one. */
  private class FallbackProgressManager : CoreProgressManager() {
    private val fallback = EmptyProgressIndicator()

    override fun getProgressIndicator(): ProgressIndicator? = super.getProgressIndicator() ?: fallback
  }

  @Test
  fun `finished process leaves no top-level indicator when getProgressIndicator falls back`() {
    val manager = FallbackProgressManager()
    manager.executeProcessUnderProgress({
      assertNotNull(manager.currentProgressModality)
    }, EmptyProgressIndicator())

    assertNull(manager.currentProgressModality)
  }

  @Test
  fun `progress dump identifies platform and virtual threads`() {
    val manager = CoreProgressManager()
    for (builder in listOf(Thread.ofPlatform(), Thread.ofVirtual())) {
      CompletableFuture.runAsync({
        manager.runProcess({
          val thread = Thread.currentThread()
          val dump = ProgressIndicatorDumper.dumpProgressIndicatorState()
          assertTrue(dump.contains("${thread.name} Id=${thread.threadId()} ${thread.state}"), dump)
        }, EmptyProgressIndicator())
      }, { builder.name("progress-dump-test").start(it) }).get(10, TimeUnit.SECONDS)
    }
  }
}
