// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path


class ZipReaderTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun testReadEmptyZip() {
    val archive = tempDir.resolve("empty.jar")
    archive.createParentDirectories()
    val directory = tempDir.resolve("empty-dir")
    directory.createDirectories()
    zip(
      targetFile = archive,
      dirs = mapOf(directory to ""),
      overwrite = true,
    )
    val names = ArrayList<String>()
    readZipFile(archive) { name, _ ->
      names.add(name)
    }
    Assertions.assertTrue(names.isEmpty())
  }
}