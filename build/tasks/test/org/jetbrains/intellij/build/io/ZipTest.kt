// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.write
import com.intellij.util.lang.ImmutableZipFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.configuration.ConfigurationProvider
import org.jetbrains.intellij.build.tasks.DirSource
import org.jetbrains.intellij.build.tasks.buildJar
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
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
    val tasks = mutableListOf<ForkJoinTask<*>>()
    // force init of AssertJ to avoid ClosedByInterruptException on reading FileLoader index
    ConfigurationProvider.CONFIGURATION_PROVIDER
    for (i in 0..100) {
      tasks.add(ForkJoinTask.adapt(Runnable {
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
      }))
    }
    ForkJoinTask.invokeAll(tasks)
  }

  @Test
  fun `do not compress jars and images`() {
    val random = Random(42)

    val dir = tempDir.newPath("/dir")
    Files.createDirectories(dir)
    val fileDescriptors = listOf(
      Entry("lib.jar", true), Entry("lib.zip", true), Entry("image.png", true), Entry("scalable-image.svg", true), Entry("readme.txt", true)
    )
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
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(32)))
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
    zipFile.use {
      for (name in list) {
        assertThat(zipFile.getEntry("test/$name")).isNotNull()
      }
    }
  }

  @Test
  fun excludes() {
    val random = Random(42)

    val dir = tempDir.newPath("/dir")
    Files.createDirectories(dir)
    val list = mutableListOf<String>()
    for (i in 0..10) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(128)))
    }

    Files.write(dir.resolve("do-not-ignore-me"), random.nextBytes(random.nextInt(128)))
    Files.write(dir.resolve("test-relative-ignore"), random.nextBytes(random.nextInt(128)))
    val iconRobotsFile = dir.resolve("some/nested/dir/icon-robots.txt")
    iconRobotsFile.write("text")
    val rootIconRobotsFile = dir.resolve("icon-robots.txt")
    rootIconRobotsFile.write("text2")

    val archiveFile = tempDir.newPath("/archive.zip")
    val fs = dir.fileSystem
    buildJar(archiveFile, listOf(DirSource(dir = dir, excludes = listOf(
      fs.getPathMatcher("glob:**/entry-item*"),
      fs.getPathMatcher("glob:test-relative-ignore"),
      fs.getPathMatcher("glob:**/icon-robots.txt"),
    ))), logger = null)

    val zipFile = ImmutableZipFile.load(archiveFile)
    zipFile.use {
      for (name in list) {
        assertThat(zipFile.getEntry("test/$name")).isNull()
      }
      assertThat(zipFile.getEntry("do-not-ignore-me")).isNotNull()
      assertThat(zipFile.getEntry("test-relative-ignore")).isNull()
      assertThat(zipFile.getEntry("some/nested/dir/icon-robots.txt")).isNull()
      assertThat(zipFile.getEntry("jjmnh")).isNull()
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
    zipFile.use {
      for (name in zipFile.entries) {
        val entry = zipFile.getEntry("samples/nested_dir/__init__.py")
        assertThat(entry).isNotNull()
        assertThat(String(entry.getData(zipFile), Charsets.UTF_8)).isEqualTo("\n")
      }
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
