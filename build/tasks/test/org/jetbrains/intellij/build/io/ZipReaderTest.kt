// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.io.createParentDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class ZipReaderTest {
  @Test
  fun testReadEmptyZip(@TempDir tempDir: Path) {
    val archive = tempDir.resolve("empty.jar")
    archive.createParentDirectories()
    zipWithPackageIndex(targetFile = archive, dir = tempDir.resolve("empty-dir"))
    val names = ArrayList<String>()
    readZipFile(archive) { name, _ ->
      names.add(name)
      ZipEntryProcessorResult.CONTINUE
    }
    assertThat(names).isEmpty()
  }
}