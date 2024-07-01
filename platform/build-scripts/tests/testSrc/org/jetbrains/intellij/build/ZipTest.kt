// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.write
import com.intellij.util.lang.HashMapZipFile
import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.configuration.ConfigurationProvider
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.io.zipWithCompression
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.random.Random

class ZipTest {
  @RegisterExtension
  @JvmField
  // not used in every test because we want to check the real FS behavior
  val fs = InMemoryFsExtension()

  @Test
  fun `interrupt thread`(@TempDir tempDir: Path) {
    val (list, archiveFile) = createLargeArchive(128, tempDir)
    checkZip(archiveFile) { zipFile ->
      val tasks = mutableListOf<ForkJoinTask<*>>()
      // force init of AssertJ to avoid ClosedByInterruptException on reading FileLoader index
      ConfigurationProvider.CONFIGURATION_PROVIDER
      for (i in 0..100) {
        tasks.add(ForkJoinTask.adapt(Runnable {
          val ioThread = runInThread {
            while (!Thread.currentThread().isInterrupted) {
              for (name in list) {
                assertThat(zipFile.getResource(name)).isNotNull()
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
  }

  @Test
  fun `read zip file with more than 65K entries`() {
    assumeTrue(SystemInfoRt.isUnix)

    val (list, archiveFile) = createLargeArchive(Short.MAX_VALUE * 2 + 20, fs.root)
    checkZip(archiveFile) { zipFile ->
      for (name in list) {
        assertThat(zipFile.getResource(name)).isNotNull()
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
    zip(archiveFile, mapOf(dir to ""))
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
    zip(archiveFile, mapOf(dir to "test"))

    checkZip(archiveFile) { zipFile ->
      for (name in list) {
        val path = "test/$name"
        assertThat(zipFile.getResource(path)).describedAs("Entry $path not found").isNotNull()
      }
    }
  }

  @Test
  fun excludes(@TempDir tempDir: Path) = runBlocking {
    val random = Random(42)

    val dir = Files.createDirectories(tempDir.resolve("dir"))
    val list = mutableListOf<String>()
    for (i in 0..10) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(128)))
    }

    Files.write(dir.resolve("do-not-ignore-me"), random.nextBytes(random.nextInt(128)))
    Files.write(dir.resolve("test-relative-ignore"), random.nextBytes(random.nextInt(128)))

    dir.resolve("some/nested/dir/icon-robots.txt").write("text")
    dir.resolve("some/nested/dir/hello.txt").write("text2")
    val rootIconRobotsFile = dir.resolve("icon-robots.txt")
    rootIconRobotsFile.write("text2")

    val archiveFile = tempDir.resolve("archive.zip")
    val fs = dir.fileSystem
    buildJar(archiveFile, listOf(DirSource(dir = dir, excludes = listOf(
      fs.getPathMatcher("glob:**/entry-item*"),
      fs.getPathMatcher("glob:test-relative-ignore"),
      fs.getPathMatcher("glob:**/icon-robots.txt"),
    ))))

    checkZip(archiveFile) { zipFile ->
      if (zipFile is ImmutableZipFile) {
        assertThat(zipFile.getOrComputeNames()).containsOnly(
          "do-not-ignore-me",
          "entry-item-1326272896-6",
          "entry-item-2145949183-8",
          "entry-item-245744780-1",
          "entry-item-942605861-5",
          "entry-item1578011503-7",
          "entry-item1705343313-9",
          "entry-item1791766502-3",
          "entry-item663137163-10",
          "entry-item828400960-4",
          "entry-item949746295-2",
          "entry-item972016666-0",
          "icon-robots.txt",
          "some/nested/dir/hello.txt",
          "some",
          "some/nested",
          "some/nested/dir"
        )
      }

      var found = ""
      zipFile.processResources("some/nested/dir", Predicate { true }, BiConsumer { name, _ ->
        found = name
      })
      assertThat(found).isEqualTo("some/nested/dir/hello.txt")

      for (name in list) {
        assertThat(zipFile.getResource("test/$name")).isNull()
      }
      assertThat(zipFile.getResource("do-not-ignore-me")).isNotNull()
      assertThat(zipFile.getResource("test-relative-ignore")).isNull()
      assertThat(zipFile.getResource("some/nested/dir/icon-robots.txt")).isNull()
      assertThat(zipFile.getResource("unknown")).isNull()
    }
  }

  @Test
  fun excludesInZipSource(@TempDir tempDir: Path) = runBlocking {
    val random = Random(42)

    val dir = Files.createDirectories(tempDir.resolve("zip"))
    Files.write(dir.resolve("zip-included"), random.nextBytes(random.nextInt(128)))
    Files.write(dir.resolve("zip-excluded"), random.nextBytes(random.nextInt(128)))
    val zip = tempDir.resolve("test.zip")
    zip(zip, mapOf(dir to ""))

    val archiveFile = tempDir.resolve("archive.zip")
    val regex = Regex("^zip-excl.*")
    buildJar(archiveFile, listOf(
      ZipSource(file = zip, distributionFileEntryProducer = null, filter = { name -> !regex.matches(name)})
    ))

    checkZip(archiveFile) { zipFile ->
      if (zipFile is ImmutableZipFile) {
        assertThat(zipFile.getOrComputeNames()).containsExactly("zip-included")
      }
    }
  }

  @Test
  fun skipIndex(@TempDir tempDir: Path) = runBlocking {
    val dir = Files.createDirectories(tempDir.resolve("dir"))
    Files.writeString(dir.resolve("file1"), "1")
    Files.writeString(dir.resolve("file2"), "2")

    val archiveFile = tempDir.resolve("archive.zip")
    buildJar(archiveFile, listOf(DirSource(dir = dir, excludes = emptyList())), compress = true)

    java.util.zip.ZipFile(archiveFile.toString()).use { zipFile ->
      assertThat(zipFile.entries().asSequence().map { it.name }.toList())
        .containsExactlyInAnyOrder("file1", "file2")
    }

    checkZip(archiveFile) { }
  }

  @Test
  fun `small file`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    val file = dir.resolve("samples/nested_dir/__init__.py")
    Files.createDirectories(file.parent)
    Files.writeString(file, "\n")

    val archiveFile = tempDir.resolve("archive.zip")
    zipWithCompression(archiveFile, mapOf(dir to ""))

    HashMapZipFile.load(archiveFile).use { zipFile ->
      val entry = zipFile.getRawEntry("samples/nested_dir/__init__.py")
      assertThat(entry).isNotNull()
      assertThat(entry!!.isCompressed).isFalse()
      assertThat(entry.getData(zipFile).decodeToString()).isEqualTo("\n")
    }
  }

  @Test
  fun `compress small`(@TempDir tempDir: Path) {
    doCompressTest(tempDir = tempDir, fileSize = 12 * 1024)
  }

  @Test
  fun `compress large`(@TempDir tempDir: Path) {
    doCompressTest(tempDir = tempDir, fileSize = 15 * 1024 * 1024)
  }

  private fun doCompressTest(tempDir: Path, fileSize: Int) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val random = Random(42)
    Files.newOutputStream(dir.resolve("file")).use {
      for (chunkSize in splitIntoChunks(size = fileSize, chunkSize = 32 * 1024)) {
        it.write(random.nextBytes(chunkSize))
      }
    }

    val archiveFile = tempDir.resolve("archive.zip")
    zipWithCompression(archiveFile, mapOf(dir to ""))

    HashMapZipFile.load(archiveFile).use { zipFile ->
      val entry = zipFile.getRawEntry("file")
      assertThat(entry).isNotNull()
      assertThat(entry!!.isCompressed).isTrue()
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
    zip(archiveFile, mapOf(dir to ""))

    checkZip(archiveFile) { zipFile ->
      val entry = zipFile.getResource("largeFile1")
      assertThat(entry).isNotNull()
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
    zipWithCompression(archiveFile, mapOf(dir to ""))

    checkZip(archiveFile) { zipFile ->
      val entry = zipFile.getResource("largeFile1")
      assertThat(entry).isNotNull()
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
    zipWithCompression(archiveFile, mapOf(dir to ""))

    checkZip(archiveFile) { zipFile ->
      val entry = zipFile.getResource("largeFile1")
      assertThat(entry).isNotNull()
    }
  }

  @Test
  fun `write all dir entries`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)

    val random = Random(42)
    val data = random.nextBytes(2 * 1024 * 1024)

    dir.resolve("dir/subDir/foo.class").write(data)
    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), addDirEntriesMode = AddDirEntriesMode.ALL)
    HashMapZipFile.load(archiveFile).use { zipFile ->
      assertThat(zipFile.getRawEntry("dir/subDir")).isNotNull
    }
  }

  // check both IKV- and non-IKV variants of an immutable zip file
  private fun checkZip(file: Path, checker: (ZipFile) -> Unit) {
    HashMapZipFile.load(file).use { zipFile ->
      checker(zipFile)
    }
    ImmutableZipFile.load(file).use { zipFile ->
      checker(zipFile)
    }
  }
}

private fun runInThread(block: () -> Unit): Thread {
  val thread = Thread(block, "test interrupt")
  thread.isDaemon = true
  thread.start()
  return thread
}

private fun splitIntoChunks(size: Int, @Suppress("SameParameterValue") chunkSize: Int): Sequence<Int> = sequence {
  var remaining = size
  while (remaining > 0) {
    val chunk = if (remaining >= chunkSize) chunkSize else remaining
    yield(chunk)
    remaining -= chunk
  }
}