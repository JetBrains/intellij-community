// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.SystemProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.impl.compilation.fetchAndUnpackCompiledClasses
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
  fun testUnpack() = runBlocking(Dispatchers.Default) {
    val metadataFile = Path.of(SystemProperties.getUserHome(), "projects/idea/out/compilation-archive/metadata.json")
    assumeTrue(Files.exists(metadataFile))

    // do not use Junit TempDir - it is very slow
    val outDir = Files.createTempDirectory("CompilationCacheTest")
    try {
      fetchAndUnpackCompiledClasses(
        reportStatisticValue = { _, _ -> },
        // parent of classOutput dir is used as a cache dir, so, do not pass temp dir directly as classOutput
        classOutput = outDir.resolve("classes"),
        metadataFile = metadataFile,
        skipUnpack = false,
        saveHash = false,
      )
    }
    finally {
      if (SystemInfoRt.isMac) {
        println("Remove compilation cache directory: $outDir")
        ProcessBuilder("rsync", "-a", "--delete", "--quiet", "/tmp/emptyDir/", outDir.toAbsolutePath().normalize().toString() + "/")
          .inheritIO()
          .directory(outDir.toFile())
          .start()
          .waitFor()
      }
      else {
        NioFiles.deleteRecursively(outDir)
      }
    }
  }
}