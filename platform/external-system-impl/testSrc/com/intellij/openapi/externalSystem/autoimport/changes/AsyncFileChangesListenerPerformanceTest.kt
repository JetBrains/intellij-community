// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.INTERNAL
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncSupplier
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@StressTestApplication
@PerformanceUnitTest
class AsyncFileChangesListenerPerformanceTest {

  @ParameterizedTest
  @CsvSource("10000")
  fun `many single events after many batch events`(eventCount: Int) {

    lateinit var listener: AsyncFileChangesListener

    fun setup() {
      listener = AsyncFileChangesListener(
        filesProvider = AsyncSupplier.blocking { emptySet() },
        changesListener = object : FilesChangesListener {},
      )
    }

    fun test() {
      // many batch events
      listener.init()
      repeat(eventCount) {
        listener.onFileChange("/path/to/file/$it.java", it.toLong(), INTERNAL)
      }
      listener.apply()

      // many single events
      repeat(eventCount) {
        listener.init()
        listener.onFileChange("/path/to/file/$it.java", it.toLong(), INTERNAL)
        listener.apply()
      }
    }

    Benchmark.newBenchmark("init and apply for $eventCount events", ::test)
      .setup(::setup)
      .runAsStressTest()
      .start()
  }
}
