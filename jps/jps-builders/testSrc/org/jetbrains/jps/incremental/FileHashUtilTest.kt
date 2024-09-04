// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental

import com.dynatrace.hash4j.hashing.Hashing
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class FileHashUtilTest {
  @Test
  fun `hash of a small file`(@TempDir dir: Path) {
    doTest(dir, "hello".toByteArray())
  }

  @Test
  fun `hash of a large file`(@TempDir dir: Path) {
    doTest(dir, Random.nextBytes(1 * 1024 * 1024))
  }

  private fun doTest(dir: Path, data: ByteArray) {
    val file = dir.resolve("test.txt")
    Files.write(file, data)
    assertThat(FileHashUtil.getFileHash(file)).isEqualTo(Hashing.komihash5_0().hashStream().putBytes(data).putLong(data.size.toLong()).asLong)
  }
}