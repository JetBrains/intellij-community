// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SlowZipTest {
  @Test
  fun `read zip file with more than 65K entries`(@TempDir tempDir: Path) = runBlocking {
    assumeTrue(SystemInfoRt.isUnix)

    val (list, archiveFile) = createLargeArchive(Short.MAX_VALUE * 2 + 20, tempDir)
    checkZip(archiveFile) { zipFile ->
      for (name in list) {
        assertThat(zipFile.getResource(name)).isNotNull()
      }
    }
  }
}