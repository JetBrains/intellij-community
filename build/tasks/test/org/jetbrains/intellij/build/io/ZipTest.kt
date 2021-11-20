// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.write
import com.intellij.util.lang.ImmutableZipFile
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.configuration.ConfigurationProvider
import org.jetbrains.intellij.build.tasks.DirSource
import org.jetbrains.intellij.build.tasks.buildJar
import org.jetbrains.intellij.build.tasks.dir
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import kotlin.random.Random

@Suppress("UsePropertyAccessSyntax")
class ZipTest {
  @RegisterExtension
  @JvmField
  // not used in every test because we want to check the real FS behaviour
  val fs = InMemoryFsExtension()

  @Test
  fun `interrupt thread`(@TempDir tempDir: Path) {
    val (list, archiveFile) = createLargeArchive(128, tempDir)
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
  fun `read zip file with more than 65K entries`() {
    Assumptions.assumeTrue(SystemInfoRt.isUnix)

    val (list, archiveFile) = createLargeArchive(Short.MAX_VALUE * 2 + 20, fs.root)
    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in list) {
        assertThat(zipFile.getEntry(name)).isNotNull()
      }
    }
  }

  private fun createLargeArchive(size: Int, tempDir: Path): Pair<MutableList<String>, Path> {
    val random = Random(42)

    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val list = mutableListOf<String>()
    for (i in 0..size) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(32)))
    }

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), compress = false)
    return Pair(list, archiveFile)
  }

  @Test
  fun `custom prefix`(@TempDir tempDir: Path) {
    val random = Random(42)

    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val list = mutableListOf<String>()
    for (i in 0..10) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(128)))
    }

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to "test"), compress = false)

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in list) {
        assertThat(zipFile.getEntry("test/$name")).isNotNull()
      }
    }
  }

  @Test
  fun excludes(@TempDir tempDir: Path) {
    val random = Random(42)

    val dir = tempDir.resolve("dir")
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

    val archiveFile = tempDir.resolve("archive.zip")
    val fs = dir.fileSystem
    buildJar(archiveFile, listOf(DirSource(dir = dir, excludes = listOf(
      fs.getPathMatcher("glob:**/entry-item*"),
      fs.getPathMatcher("glob:test-relative-ignore"),
      fs.getPathMatcher("glob:**/icon-robots.txt"),
    ))))

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in list) {
        assertThat(zipFile.getEntry("test/$name")).isNull()
      }
      assertThat(zipFile.getEntry("do-not-ignore-me")).isNotNull()
      assertThat(zipFile.getEntry("test-relative-ignore")).isNull()
      assertThat(zipFile.getEntry("some/nested/dir/icon-robots.txt")).isNull()
      assertThat(zipFile.getEntry("unknown")).isNull()
    }
  }

  @Test
  fun `small file`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    val file = dir.resolve("samples/nested_dir/__init__.py")
    Files.createDirectories(file.parent)
    Files.writeString(file, "\n")

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), compress = true)

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in zipFile.entries) {
        val entry = zipFile.getEntry("samples/nested_dir/__init__.py")
        assertThat(entry).isNotNull()
        assertThat(entry!!.isCompressed()).isFalse()
        assertThat(String(entry.getData(zipFile), Charsets.UTF_8)).isEqualTo("\n")
      }
    }
  }

  @Test
  fun symlink(@TempDir tempDir: Path) {
    Assumptions.assumeTrue(SystemInfoRt.isUnix)

    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)

    val targetFile = dir.resolve("target")
    Files.writeString(targetFile, "target")
    Files.createSymbolicLink(dir.resolve("link"), targetFile)

    val zipFile = tempDir.resolve("file.zip")
    writeNewFile(zipFile) { outFileChannel ->
      ZipArchiveOutputStream(outFileChannel).use { out ->
        out.dir(dir, "")
      }
    }
  }

  @Test
  fun compression(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val data = Random(42).nextBytes(4 * 1024)
    Files.write(dir.resolve("file"), data + data + data)

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), compress = true)

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in zipFile.entries) {
        val entry = zipFile.getEntry("file")
        assertThat(entry).isNotNull()
        assertThat(entry!!.isCompressed()).isTrue()
      }
    }
  }

  @Test
  fun `large file`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val random = Random(42)
    Files.write(dir.resolve("largeFile1"), random.nextBytes(10 * 1024 * 1024))
    Files.write(dir.resolve("largeFile2"), random.nextBytes(1 * 1024 * 1024))
    Files.write(dir.resolve("largeFile3"), random.nextBytes(2 * 1024 * 1024))

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), compress = false)

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in zipFile.entries) {
        val entry = zipFile.getEntry("largeFile1")
        assertThat(entry).isNotNull()
      }
    }
  }

  @Test
  fun `large incompressible file compressed`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val random = Random(42)
    val data = random.nextBytes(10 * 1024 * 1024)

    Files.write(dir.resolve("largeFile1"), data)
    Files.write(dir.resolve("largeFile2"), random.nextBytes(1 * 1024 * 1024))
    Files.write(dir.resolve("largeFile3"), random.nextBytes(2 * 1024 * 1024))

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), compress = true)

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in zipFile.entries) {
        val entry = zipFile.getEntry("largeFile1")
        assertThat(entry).isNotNull()
      }
    }
  }

  @Test
  fun `large compressible file compressed`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val random = Random(42)
    val data = random.nextBytes(2 * 1024 * 1024)

    Files.write(dir.resolve("largeFile1"), data + data + data + data + data + data + data + data + data + data)
    Files.write(dir.resolve("largeFile2"), data + data + data + data)
    Files.write(dir.resolve("largeFile3"), data + data)

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), compress = true)

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      for (name in zipFile.entries) {
        val entry = zipFile.getEntry("largeFile1")
        assertThat(entry).isNotNull()
      }
    }
  }
}

private fun runInThread(block: () -> Unit): Thread {
  val thread = Thread(block, "test interrupt")
  thread.isDaemon = true
  thread.start()
  return thread
}
