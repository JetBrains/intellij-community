// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.zip.ImmutableZipFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.configuration.ConfigurationProvider
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import kotlin.random.Random

@Suppress("UsePropertyAccessSyntax")
class ZipTest {
  @JvmField
  @Rule
  val tempDir = TemporaryDirectory()

  @Test
  fun `interrupt thread`() {
    val (list, archiveFile) = createLargeArchive(128)
    val zipFile = ImmutableZipFile.load(archiveFile)
    val executor = Executors.newWorkStealingPool(4)
    // force init of AssertJ to avoid ClosedByInterruptException on reading FileLoader index
    ConfigurationProvider.CONFIGURATION_PROVIDER
    for (i in 0..100) {
      executor.execute {
        val ioThread = runInThread {
          while (!Thread.currentThread().isInterrupted()) {
            for (name in list) {
              assertThat(zipFile.getEntry(name)).isNotNull()
            }
          }
        }

        // once in a while, the IO thread is stopped
        Thread.sleep(50)
        ioThread.interrupt()
        Thread.sleep(10)
        ioThread.join()
      }
    }
    executor.shutdown()
    executor.awaitTermination(4, TimeUnit.MINUTES)
  }

  @Test
  fun `do not compress jars and images`() {
    val random = Random(42)

    val dir = tempDir.newPath("/dir")
    Files.createDirectories(dir)
    val fileDescriptors = listOf(Entry("lib.jar", true), Entry("lib.zip", true), Entry("image.png", true), Entry("scalable-image.svg", true), Entry("readme.txt", true))
    for (entry in fileDescriptors) {
      Files.write(dir.resolve(entry.path), random.nextBytes(1024))
    }

    val archiveFile = tempDir.newPath("/archive.zip")
    zip(archiveFile, mapOf(dir to ""))

    val zipFile = ImmutableZipFile.load(archiveFile)
    for (entry in fileDescriptors) {
      assertThat(zipFile.getEntry(entry.path).method)
        .describedAs(entry.path)
        .isEqualTo(if (entry.isCompressed) ZipEntry.DEFLATED else ZipEntry.STORED)
    }
  }

  @Test
  fun `read zip file with more than 65K entries`() {
    val (list, archiveFile) = createLargeArchive(Short.MAX_VALUE * 2)
    val zipFile = ImmutableZipFile.load(archiveFile)
    for (name in list) {
      assertThat(zipFile.getEntry(name)).isNotNull()
    }
  }

  private fun createLargeArchive(size: Int): Pair<MutableList<String>, Path> {
    val random = Random(42)

    val dir = tempDir.newPath("/dir")
    Files.createDirectories(dir)
    val list = mutableListOf<String>()
    for (i in 0..size) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(128)))
    }

    val archiveFile = tempDir.newPath("/archive.zip")
    zip(archiveFile, mapOf(dir to ""))
    return Pair(list, archiveFile)
  }

  @Test
  fun `custom prefix`() {
    val random = Random(42)

    val dir = tempDir.newPath("/dir")
    Files.createDirectories(dir)
    val list = mutableListOf<String>()
    for (i in 0..10) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(128)))
    }

    val archiveFile = tempDir.newPath("/archive.zip")
    zip(archiveFile, mapOf(dir to "test"))

    val zipFile = ImmutableZipFile.load(archiveFile)
    for (name in list) {
      assertThat(zipFile.getEntry("test/$name")).isNotNull()
    }
  }

  @Test
  fun `small file`() {
    val dir = tempDir.newPath("/dir")
    val file = dir.resolve("samples/nested_dir/__init__.py")
    Files.createDirectories(file.parent)
    Files.writeString(file, "\n")

    val archiveFile = tempDir.newPath("/archive.zip")
    zip(archiveFile, mapOf(dir to ""))

    val zipFile = ImmutableZipFile.load(archiveFile)
    for (name in zipFile.entries) {
      val entry = zipFile.getEntry("samples/nested_dir/__init__.py")
      assertThat(entry).isNotNull()
      assertThat(String(entry.getData(zipFile), Charsets.UTF_8)).isEqualTo("\n")
    }
  }
}

private class Entry(val path: String, val isCompressed: Boolean)

private fun runInThread(block: () -> Unit): Thread {
  val thread = Thread(block, "test interrupt")
  thread.isDaemon = true
  thread.start()
  return thread
}
