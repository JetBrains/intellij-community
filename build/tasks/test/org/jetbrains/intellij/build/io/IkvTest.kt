// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.util.lang.Ikv
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

private fun generateDb(file: Path, count: Int, random: Random): List<Pair<Long, ByteArray>> {
  Files.createDirectories(file.parent)
  val list = ArrayList<Pair<Long, ByteArray>>(count)
  sizeAwareIkvWriter(file).use { writer ->
    (0 until count).forEach { i ->
      val data = random.nextBytes(random.nextInt(64, 512))
      val key = Hashing.xxh3_64().hashBytesToLong(data)
      writer.write(writer.entry(key, data.size), data)
      list.add(Pair(key, data))
    }
  }
  return list
}

internal class IkvTest {
  private val random = Random(42)

  @TempDir
  @JvmField
  var tempDir: Path? = null

  @Test
  fun singleKey() {
    val file = tempDir!!.resolve("db")

    val data = random.nextBytes(random.nextInt(64, 512))
    val key = Hashing.xxh3_64().hashBytesToLong(data)

    Files.createDirectories(file.parent)
    sizeAwareIkvWriter(file).use { writer ->
      writer.write(writer.entry(key, data.size), data)
    }

    Ikv.loadSizeAwareIkv(file).use {
      assertThat(it.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
    }
  }

  //@Test
  //fun app() {
  //  val file = Path.of("/Users/develar/Desktop/app.jar")
  //  val zip = ImmutableZipFile.load(file)
  //  val time = measureTimeMillis {
  //    repeat(100_00) {
  //      val byteBuffer = zip.getByteBuffer("ai/grazie/DataHolder.class")
  //      if (byteBuffer!!.position() < 0) {
  //        println(12)
  //      }
  //    }
  //  }
  //  println(time)
  //}

  @Test
  fun `two keys`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("db")

    val list = generateDb(file = file, count = 2, random = random)
    Ikv.loadSizeAwareIkv(file).use {
      for ((key, data) in list) {
        assertThat(it.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [10, 16, 32, 64, 128, 200, 1000, 1_024, 2_048, 5000, 10_000])
  fun manyKeys(keyCount: Int) {
    val file = tempDir!!.resolve("db")

    val list = generateDb(file = file, count = keyCount, random = random)
    Ikv.loadSizeAwareIkv(file).use { ikv ->
      for ((key, data) in list) {
        assertThat(ikv.getValue(key)).isEqualTo(ByteBuffer.wrap(data))
      }
    }
  }
}