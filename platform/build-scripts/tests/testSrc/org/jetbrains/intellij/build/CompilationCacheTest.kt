// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.impl.compilation.fetchAndUnpackCompiledClasses
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CompilationCacheTest {
  companion object {
    @AfterAll
    @JvmStatic
    fun flushTracer() {
      runBlocking {
        TraceManager.shutdown()
      }
    }
  }

  @Test
  fun testUnpack() {
    val metadataFile = Path.of("/Volumes/data/Documents/idea/out/compilation-archive/metadata.json")
    assumeTrue(Files.exists(metadataFile))

    // do not use Junit TempDir - it is very slow
    val outDir = Files.createTempDirectory("CompilationCacheTest")
    try {
      fetchAndUnpackCompiledClasses(
        reportStatisticValue = { _, _ -> },
        withScope = { _, operation -> operation() },
        // parent of classOutput dir is used as a cache dir, so, do not pass temp dir directly as classOutput
        classOutput = outDir.resolve("classes"),
        metadataFile = metadataFile,
        skipUnpack = false,
        saveHash = false,
      )
    }
    finally {
      Files.list(outDir).parallel().use { stream ->
        stream.forEach(::deleteDir)
      }
      Files.delete(outDir)
    }
  }
}