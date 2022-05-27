// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.io.exists
import org.jetbrains.intellij.build.impl.compilation.fetchAndUnpackCompiledClasses
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CompilationCacheTest {
  companion object {
    @AfterAll
    @JvmStatic
    fun flushTracer() {
      TraceManager.finish()
    }
  }

  @Test
  fun testUnpack(@TempDir outDir: Path) {
    val metadataFile = Path.of("/Volumes/data/Documents/idea/out-zips/metadata.json")
    assumeTrue(metadataFile.exists())

    fetchAndUnpackCompiledClasses(
      reportStatisticValue = { _, _ -> },
      // parent of classOutput dir is used as a cache dir, so, do not pass temp dir directly as classOutput
      classOutput = outDir.resolve("classes"),
      metadataFile = metadataFile,
      saveHash = false,
    )
  }
}