// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.ZipIndexWriter
import org.jetbrains.intellij.build.io.archiveDirToZipWriter
import org.jetbrains.intellij.build.io.testOnlyDataWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import kotlin.random.Random
import kotlin.time.measureTime

object ZipTestBenchmarkFC {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      measure(useMapped = false)
    }
  }
}

object ZipTestBenchmarkMP {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      measure(useMapped = true)
    }
  }
}

object ZipTestBenchmarkFCf {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      measureWriteOnTheFly(useMapped = false)
    }
  }
}

object ZipTestBenchmarkMPf {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      measureWriteOnTheFly(useMapped = true)
    }
  }
}

private val largeModules = listOf("intellij.ruby", "intellij.python.helpers", "intellij.android.core", "intellij.platform.ide.impl",
                                  "intellij.javascript.impl", "intellij.platform.lang.impl", "intellij.php.impl", "intellij.rider")

private suspend fun measure(useMapped: Boolean) {
  val tempDir = Files.createTempDirectory("zip-measure-")
  val outDir = Path.of(System.getProperty("user.home"), "projects/idea/out/classes/production")
  try {
    val duration = measureTime {
      withContext(Dispatchers.IO) {
        for (moduleName in largeModules) {
          launch {
            doArchive(outDir.resolve(moduleName), tempDir.resolve("archive-$moduleName.zip"), useMapped)
          }
        }
      }
    }
    println("$duration")
  }
  finally {
    NioFiles.deleteRecursively(tempDir)
  }
}

private suspend fun measureWriteOnTheFly(useMapped: Boolean) {
  val tempDir = Files.createTempDirectory("zip-measure-")
  try {
    val duration = measureTime {
      withContext(Dispatchers.IO) {
        repeat(8) { fileNumber ->
          launch {
            val random = Random(42)
            val crc32 = CRC32()
            ZipArchiveOutputStream(testOnlyDataWriter(tempDir.resolve("archive-$fileNumber.zip"), useMapped = useMapped), ZipIndexWriter(null)).use { writer ->
              repeat(10_000) { number ->
                writer.writeDataWithUnknownSize("$number-entry".toByteArray(), -1, crc32) { buffer ->
                  buffer.writeInt(number)
                  buffer.writeLong((number * number).toLong())
                  buffer.writeBytes(random.nextBytes(random.nextInt(8192)))
                }
              }
            }
          }
        }
      }
    }
    println("$duration")
  }
  finally {
    NioFiles.deleteRecursively(tempDir)
  }
}

private fun doArchive(dir: Path, targetFile: Path, useMapped: Boolean) {
  val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
  ZipFileWriter(ZipArchiveOutputStream(testOnlyDataWriter(targetFile, useMapped = useMapped), ZipIndexWriter(packageIndexBuilder))).use { zipFileWriter ->
    archiveDirToZipWriter(
      zipFileWriter = zipFileWriter,
      fileAdded = { name, _ ->
        packageIndexBuilder.addFile(name)
        true
      },
      dirs = mapOf(dir to ""),
    )
  }
}