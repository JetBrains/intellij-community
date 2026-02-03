// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.io.MappedFileDataWriter
import org.jetbrains.intellij.build.io.RW
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.ZipIndexWriter
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import kotlin.random.Random

class MappedZipWriterTest {
  @Test
  fun `various chunk size`(@TempDir tempDir: Path) {
    assumeTrue(System.getenv("TEAMCITY_VERSION") == null)

    Files.createDirectories(tempDir)

    val random = Random(42)
    runBlocking(Dispatchers.IO.limitedParallelism(8)) {
      repeat(36) { count ->
        launch {
          val archiveFile = tempDir.resolve("archive-${count}.zip")
          val chunkSize = random.nextInt(1024, 64 * 1024 * 1024)
          val fileCount = random.nextInt(1, 100)
          try {
            doTest(mappedChunkSize = chunkSize, fileCount = fileCount, archiveFile = archiveFile, random = random)
          }
          catch (e: Exception) {
            throw RuntimeException("Failed (chunkSize=$chunkSize, fileCount=$fileCount)", e)
          }
          finally {
            Files.deleteIfExists(archiveFile)
          }
        }
      }
    }
  }

  //@Test
  //fun `chunk size`(@TempDir tempDir: Path) {
  //  Files.createDirectories(tempDir)
  //
  //  val archiveFile = tempDir.resolve("archive.zip")
  //  val chunkSize = 179952404L
  //  val fileCount = 71
  //  val random = Random(42)
  //  doTest(mappedChunkSize = chunkSize, fileCount = fileCount, archiveFile = archiveFile, random = random)
  //}

  private fun doTest(mappedChunkSize: Int, fileCount: Int, archiveFile: Path, random: Random) {
    val list = ArrayList<TestEntryItem>(fileCount)
    val crc32 = CRC32()
    ZipArchiveOutputStream(MappedFileDataWriter(archiveFile, RW, mappedChunkSize), ZipIndexWriter(null)).use { writer ->
      var totalSize = 0L
      repeat(fileCount) {
        val byteSize = random.nextInt(0, 32 * 1024 * 1024)

        totalSize += byteSize + 100
        if (totalSize > Int.MAX_VALUE) {
          return@repeat
        }

        val path = "file $it"
        writer.uncompressedData(path = path.toByteArray(), data = random.nextBytes(byteSize), crc32 = crc32)
        list.add(TestEntryItem(byteSize, path))
      }
    }

    checkZip(archiveFile) { zipFile ->
      for (item in list) {
        val entry = zipFile.getData(item.name)
        assertThat(entry).isNotNull()
        assertThat(entry!!.size).isEqualTo(item.size)
      }
    }
  }
}