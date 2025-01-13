// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.h2.mvstore.MVStoreTool
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal object InspectJpsCacheDb {
  @JvmStatic
  fun main(args: Array<String>) {
    MVStoreTool.dump(SystemProperties.getUserHome() + "/projects/idea/out/test-jps-cache-compilation/jps-build-data/jps-portable-cache.db", false)
  }
}

internal object CompactJpsCacheDb {
  @JvmStatic
  fun main(args: Array<String>) {
    MVStoreTool.compact(SystemProperties.getUserHome() + "/projects/idea/out/test-jps-cache-compilation/jps-build-data/jps-portable-cache.db", false)
  }
}

class StorageManagerTest {
  companion object {
    init {
      System.setProperty("jps.source.to.output.mapping.check.collisions", "true")
    }
  }

  @Test
  fun staleMap(@TempDir tempDir: Path) {
    val file = tempDir.resolve("jps-cache.db")
    val storageManager = StorageManager(file)
    try {
      val mapping = ExperimentalSourceToOutputMapping.createSourceToOutputMap(
        storageManager = storageManager,
        relativizer = TestPathTypeAwareRelativizer,
        targetId = "test-module",
        targetTypeId = "java",
        outputToTargetMapping = null,
      )

      mapping.appendOutput("foo/bar/Baz.java", "out/bar/Baz.class")
      assertThat(mapping.getOutputs(Path.of("foo/bar/Baz.java"))).containsExactly(Path.of("out/bar/Baz.class"))

      storageManager.removeMaps(targetId = "test-module", targetTypeId = "java")
      assertThat(mapping.getOutputs(Path.of("foo/bar/Baz.java"))).isNull()
    }
    finally {
      storageManager.close()
    }
  }
}