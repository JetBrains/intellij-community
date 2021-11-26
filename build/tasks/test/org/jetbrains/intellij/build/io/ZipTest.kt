// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.write
import com.intellij.util.lang.HashMapZipFile
import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

  private val secret = byteArrayOf(0xb8.toByte(), 0xfe.toByte(), 0x6c.toByte(), 0x39.toByte(), 0x23.toByte(), 0xa4.toByte(), 0x4b.toByte(),
                                   0xbe.toByte(), 0x7c.toByte(), 0x01.toByte(), 0x81.toByte(), 0x2c.toByte(), 0xf7.toByte(), 0x21.toByte(),
                                   0xad.toByte(), 0x1c.toByte(), 0xde.toByte(), 0xd4.toByte(), 0x6d.toByte(), 0xe9.toByte(), 0x83.toByte(),
                                   0x90.toByte(), 0x97.toByte(), 0xdb.toByte(), 0x72.toByte(), 0x40.toByte(), 0xa4.toByte(), 0xa4.toByte(),
                                   0xb7.toByte(), 0xb3.toByte(), 0x67.toByte(), 0x1f.toByte(), 0xcb.toByte(), 0x79.toByte(), 0xe6.toByte(),
                                   0x4e.toByte(), 0xcc.toByte(), 0xc0.toByte(), 0xe5.toByte(), 0x78.toByte(), 0x82.toByte(), 0x5a.toByte(),
                                   0xd0.toByte(), 0x7d.toByte(), 0xcc.toByte(), 0xff.toByte(), 0x72.toByte(), 0x21.toByte(), 0xb8.toByte(),
                                   0x08.toByte(), 0x46.toByte(), 0x74.toByte(), 0xf7.toByte(), 0x43.toByte(), 0x24.toByte(), 0x8e.toByte(),
                                   0xe0.toByte(), 0x35.toByte(), 0x90.toByte(), 0xe6.toByte(), 0x81.toByte(), 0x3a.toByte(), 0x26.toByte(),
                                   0x4c.toByte(), 0x3c.toByte(), 0x28.toByte(), 0x52.toByte(), 0xbb.toByte(), 0x91.toByte(), 0xc3.toByte(),
                                   0x00.toByte(), 0xcb.toByte(), 0x88.toByte(), 0xd0.toByte(), 0x65.toByte(), 0x8b.toByte(), 0x1b.toByte(),
                                   0x53.toByte(), 0x2e.toByte(), 0xa3.toByte(), 0x71.toByte(), 0x64.toByte(), 0x48.toByte(), 0x97.toByte(),
                                   0xa2.toByte(), 0x0d.toByte(), 0xf9.toByte(), 0x4e.toByte(), 0x38.toByte(), 0x19.toByte(), 0xef.toByte(),
                                   0x46.toByte(), 0xa9.toByte(), 0xde.toByte(), 0xac.toByte(), 0xd8.toByte(), 0xa8.toByte(), 0xfa.toByte(),
                                   0x76.toByte(), 0x3f.toByte(), 0xe3.toByte(), 0x9c.toByte(), 0x34.toByte(), 0x3f.toByte(), 0xf9.toByte(),
                                   0xdc.toByte(), 0xbb.toByte(), 0xc7.toByte(), 0xc7.toByte(), 0x0b.toByte(), 0x4f.toByte(), 0x1d.toByte(),
                                   0x8a.toByte(), 0x51.toByte(), 0xe0.toByte(), 0x4b.toByte(), 0xcd.toByte(), 0xb4.toByte(), 0x59.toByte(),
                                   0x31.toByte(), 0xc8.toByte(), 0x9f.toByte(), 0x7e.toByte(), 0xc9.toByte(), 0xd9.toByte(), 0x78.toByte(),
                                   0x73.toByte(), 0x64.toByte(), 0xea.toByte(), 0xc5.toByte(), 0xac.toByte(), 0x83.toByte(), 0x34.toByte(),
                                   0xd3.toByte(), 0xeb.toByte(), 0xc3.toByte(), 0xc5.toByte(), 0x81.toByte(), 0xa0.toByte(), 0xff.toByte(),
                                   0xfa.toByte(), 0x13.toByte(), 0x63.toByte(), 0xeb.toByte(), 0x17.toByte(), 0x0d.toByte(), 0xdd.toByte(),
                                   0x51.toByte(), 0xb7.toByte(), 0xf0.toByte(), 0xda.toByte(), 0x49.toByte(), 0xd3.toByte(), 0x16.toByte(),
                                   0x55.toByte(), 0x26.toByte(), 0x29.toByte(), 0xd4.toByte(), 0x68.toByte(), 0x9e.toByte(), 0x2b.toByte(),
                                   0x16.toByte(), 0xbe.toByte(), 0x58.toByte(), 0x7d.toByte(), 0x47.toByte(), 0xa1.toByte(), 0xfc.toByte(),
                                   0x8f.toByte(), 0xf8.toByte(), 0xb8.toByte(), 0xd1.toByte(), 0x7a.toByte(), 0xd0.toByte(), 0x31.toByte(),
                                   0xce.toByte(), 0x45.toByte(), 0xcb.toByte(), 0x3a.toByte(), 0x8f.toByte(), 0x95.toByte(), 0x16.toByte(),
                                   0x04.toByte(), 0x28.toByte(), 0xaf.toByte(), 0xd7.toByte(), 0xfb.toByte(), 0xca.toByte(), 0xbb.toByte(),
                                   0x4b.toByte(), 0x40.toByte(), 0x7e.toByte())

  @Test
  fun `interrupt thread`(@TempDir tempDir: Path) {
    val buffer = ByteBuffer.wrap(secret).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    val a = IntArray(buffer.limit())
    buffer.get(12)

    val (list, archiveFile) = createLargeArchive(128, tempDir)
    checkZip(archiveFile) { zipFile ->
      val tasks = mutableListOf<ForkJoinTask<*>>()
      // force init of AssertJ to avoid ClosedByInterruptException on reading FileLoader index
      ConfigurationProvider.CONFIGURATION_PROVIDER
      for (i in 0..100) {
        tasks.add(ForkJoinTask.adapt(Runnable {
          val ioThread = runInThread {
            while (!Thread.currentThread().isInterrupted()) {
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
    Assumptions.assumeTrue(SystemInfoRt.isUnix)

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

    checkZip(archiveFile) { zipFile ->
      for (name in list) {
        assertThat(zipFile.getResource("test/$name")).isNotNull()
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

    checkZip(archiveFile) { zipFile ->
      if (zipFile is ImmutableZipFile) {
        assertThat(zipFile.getOrComputeNames()).containsExactly(
          "entry-item663137163-10",
          "entry-item972016666-0",
          "entry-item1791766502-3",
          "entry-item1705343313-9",
          "entry-item-942605861-5",
          "entry-item1578011503-7",
          "entry-item949746295-2",
          "entry-item-245744780-1",
          "do-not-ignore-me",
          "icon-robots.txt",
          "entry-item-2145949183-8",
          "entry-item-1326272896-6",
          "entry-item828400960-4"
        )
      }

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
  fun `small file`(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    val file = dir.resolve("samples/nested_dir/__init__.py")
    Files.createDirectories(file.parent)
    Files.writeString(file, "\n")

    val archiveFile = tempDir.resolve("archive.zip")
    zip(archiveFile, mapOf(dir to ""), compress = true)

    HashMapZipFile.load(archiveFile).use { zipFile ->
      for (name in zipFile.entries) {
        val entry = zipFile.getRawEntry("samples/nested_dir/__init__.py")
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

    HashMapZipFile.load(archiveFile).use { zipFile ->
      val entry = zipFile.getRawEntry("file")
      assertThat(entry).isNotNull()
      assertThat(entry!!.isCompressed()).isTrue()
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
    zip(archiveFile, mapOf(dir to ""), compress = true)

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
    zip(archiveFile, mapOf(dir to ""), compress = true)

    checkZip(archiveFile) { zipFile ->
      val entry = zipFile.getResource("largeFile1")
      assertThat(entry).isNotNull()
    }
  }

  // check both IKV- and non-IKV varians of immutable zip file
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
