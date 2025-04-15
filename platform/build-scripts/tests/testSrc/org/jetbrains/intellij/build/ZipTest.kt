// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.io.toByteArray
import com.intellij.util.io.write
import com.intellij.util.lang.HashMapZipFile
import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.configuration.ConfigurationProvider
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.ByteBufferDataWriter
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.ZipIndexWriter
import org.jetbrains.intellij.build.io.compressedData
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.io.zipWithPackageIndex
import org.jetbrains.intellij.build.io.zipWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.function.BiConsumer
import java.util.function.Predicate
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.random.Random

class ZipTest {
  @Test
  fun `interrupt thread`(@TempDir tempDir: Path) = runBlocking {
    val (list, archiveFile) = createLargeArchive(128, tempDir)
    checkZip(archiveFile) { zipFile ->
      val tasks = mutableListOf<ForkJoinTask<*>>()
      // force init of AssertJ to avoid ClosedByInterruptException on reading FileLoader index
      ConfigurationProvider.CONFIGURATION_PROVIDER
      repeat (100) {
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
    zipWithPackageIndex(zip, dir)

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
    buildJar(targetFile = archiveFile, sources = listOf(DirSource(dir)), compress = true)

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
    val data = "\n"
    Files.writeString(file, data)

    val archiveFile = tempDir.resolve("archive.zip")
    zipWithCompression(archiveFile, mapOf(dir to ""))


    java.util.zip.ZipFile(archiveFile.toFile()).use { jdkZipFile ->
      val entry = jdkZipFile.getEntry("samples/nested_dir/__init__.py")
      assertThat(entry).isNotNull()
      val crc = CRC32().also { it.update(data.toByteArray()) }.value
      assertThat(entry.crc).isEqualTo(crc)
    }

    HashMapZipFile.load(archiveFile).use { zipFile ->
      val entry = zipFile.getRawEntry("samples/nested_dir/__init__.py")
      assertThat(entry).isNotNull()
      assertThat(entry!!.isCompressed).isFalse()
      assertThat(entry.getData(zipFile).decodeToString()).isEqualTo(data)
    }
  }

  @Test
  fun undeclared(@TempDir tempDir: Path) {
    val archiveFile = tempDir.resolve("archive.zip")

    val random = Random(42)

    val list = ArrayList<TestEntryItem>(2)

    zipWriter(archiveFile, null).use {
      repeat(100) { number ->
        val item = TestEntryItem(name = "a$number.class", size = random.nextInt(4096))
        list.add(item)
        it.uncompressedData(item.name.toByteArray(), random.nextBytes(item.size), null)
        it.writeUndeclaredData(random.nextInt(number, 1 * 1024 * 1024)) { buffer, _ ->
          buffer.putInt(12)
          Int.SIZE_BYTES
        }
      }

      val item = TestEntryItem(name = "b.class", size = random.nextInt(4096))
      list.add(item)
      it.uncompressedData(item.name.toByteArray(), random.nextBytes(item.size), null)
    }

    checkZip(archiveFile) { zipFile ->
      for (item in list) {
        val entry = zipFile.getResource(item.name) ?: error("Entry ${item.name} not found")
        assertThat(entry).isNotNull()
        assertThat(entry.uncompressedSize).describedAs { "Entry ${item.name}" }.isEqualTo(item.size)
      }
    }
  }

  @Test
  fun `undeclared with known size`(@TempDir tempDir: Path) {
    val archiveFile = tempDir.resolve("archive.zip")
    val random = Random(42)
    val list = ArrayList<TestEntryItem>(3)

    class TestUndeclaredEntryItem(@JvmField val offset: Int, @JvmField val data: ByteArray)

    val undeclaredList = ArrayList<TestUndeclaredEntryItem>(2)

    zipWriter(archiveFile, null).use {
      var item = TestEntryItem(name = "a.class", size = random.nextInt(4096))
      list.add(item)
      it.uncompressedData(item.name.toByteArray(), random.nextBytes(item.size), null)
      it.writeUndeclaredDataWithKnownSize(ByteBuffer.allocate(100))

      item = TestEntryItem(name = "b.class", size = random.nextInt(4096))
      list.add(item)
      it.uncompressedData(item.name.toByteArray(), random.nextBytes(item.size), null)

      // test writing a buffer with a non-zero position
      val data = random.nextBytes(2 * 4096)
      val offset = it.writeUndeclaredDataWithKnownSize(ByteBuffer.wrap(data).position(42)).toInt()
      undeclaredList.add(TestUndeclaredEntryItem(offset = offset, data = data.copyOfRange(42, data.size)))

      item = TestEntryItem(name = "c.class", size = ZipArchiveOutputStream.INITIAL_BUFFER_CAPACITY * 8)
      // test ByteBuffer variant
      it.uncompressedData(item.name.toByteArray(), ByteBuffer.wrap(random.nextBytes(item.size)), null)
      list.add(item)
    }

    checkZip(archiveFile) { zipFile ->
      for (item in list) {
        val entry = zipFile.getResource(item.name) ?: error("Entry ${item.name} not found")
        assertThat(entry).isNotNull()
        assertThat(entry.uncompressedSize).describedAs { "Entry ${item.name}" }.isEqualTo(item.size)
      }

      if (zipFile is HashMapZipFile) {
        for (item in undeclaredList) {
          val onDisk = zipFile.__getRawSlice().slice(item.offset, item.data.size).toByteArray()
          assertThat(onDisk).isEqualTo(item.data)
        }
      }
    }
  }

  @Test
  fun `undeclared data various sizes`(@TempDir tempDir: Path) {
    val archiveFile = tempDir.resolve("archive.zip")
    val random = Random(42)
    val list = ArrayList<TestEntryItem>(20)

    class TestUndeclaredEntryItem(@JvmField val offset: Int, @JvmField val data: ByteArray)

    val undeclaredList = ArrayList<TestUndeclaredEntryItem>(20)

    val dataSizes = listOf(
      0, 1, 100, 4095, 4096, 4097,
      8191, 8192, 8193,
      16383, 16384, 16385,
      32767, 32768, 32769,
      65535, 65536, 65537,
      1024 * 1024 - 1, 1024 * 1024, 1024 * 1024 + 1, ZipArchiveOutputStream.FLUSH_THRESHOLD * 2,
    )

    zipWriter(archiveFile, null).use { stream ->
      for ((index, size) in dataSizes.withIndex()) {
        // Test uncompressed data
        val item = TestEntryItem(name = "uncompressed$index.data", size = size)
        list.add(item)
        stream.uncompressedData(item.name.toByteArray(), random.nextBytes(size), null)

        // Test undeclared data
        val data = random.nextBytes(size)
        val offset = stream.writeUndeclaredDataWithKnownSize(ByteBuffer.wrap(data)).toInt()
        undeclaredList.add(TestUndeclaredEntryItem(offset = offset, data = data))
      }
    }

    checkZip(archiveFile) { zipFile ->
      for (item in list) {
        val entry = zipFile.getResource(item.name) ?: error("Entry ${item.name} not found")
        assertThat(entry).isNotNull()
        assertThat(entry.uncompressedSize).describedAs { "Entry ${item.name}" }.isEqualTo(item.size)
      }

      if (zipFile is HashMapZipFile) {
        for (item in undeclaredList) {
          val onDisk = zipFile.__getRawSlice().slice(item.offset, item.data.size).toByteArray()
          assertThat(onDisk).isEqualTo(item.data)
        }
      }
    }
  }

  @Test
  fun `byteBuff writer`(@TempDir tempDir: Path) {
    val archiveFile = tempDir.resolve("archive.zip")

    val random = Random(42)
    val testData1 = "META-INF/pluginIcon.svg" to random.nextBytes(3453)

    zipWriter(archiveFile, null).use {
      it.writeDataWithUnknownSize(path = "keymap.jar".toByteArray(), estimatedSize = -1, crc32 = CRC32()) { byteBuf ->
        writeTestJar(byteBuf = byteBuf, testData1 = testData1, random = random, tempDir = tempDir)

        assertThat(byteBuf.readableBytes()).isEqualTo(12202)
      }
    }

    checkZip(archiveFile) { zipFile ->
      val entry = zipFile.getResource("keymap.jar")!!
      assertThat(entry).isNotNull()
      assertThat(entry.byteBuffer.remaining()).isEqualTo(12162)

      ZipInputStream(entry.inputStream).use { zipInputStream ->
        val entry = zipInputStream.nextEntry!!
        assertThat(entry.name).isEqualTo(testData1.first)
        assertThat(zipInputStream.readBytes().size).isEqualTo(testData1.second.size)
        assertThat(entry.crc).isEqualTo(getCrc(testData1.second))

        zipInputStream.closeEntry()

        assertThat(zipInputStream.nextEntry!!.name).isEqualTo("META-INF/pluginIcon_dark.svg")
        zipInputStream.closeEntry()

        assertThat(zipInputStream.nextEntry!!.name).isEqualTo("__index__")
        zipInputStream.closeEntry()

        assertThat(zipInputStream.nextEntry).isNull()
      }
    }
  }

  @Test
  fun compressedData(@TempDir tempDir: Path) = runBlocking {
    val archiveFile = tempDir.resolve("archive.zip")

    val random = Random(42)
    val testData1 = "META-INF/pluginIcon.svg" to randomCompressibleBytes(4096, random)

    zipWriter(archiveFile, null).use { outerZipWriter ->
      val byteBuf = ByteBuffer.allocate(32 * 1024)
      val nettyBuffer = Unpooled.wrappedBuffer(byteBuf).writerIndex(0)
      writeTestJar(byteBuf = nettyBuffer, testData1 = testData1, random = random, tempDir = tempDir)

      byteBuf.position(0).limit(nettyBuffer.writerIndex())

      compressedData(
        path = "keymap.jar",
        data = byteBuf,
        deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true),
        crc32 = CRC32(),
        resultStream = outerZipWriter,
      )
    }

    java.util.zip.ZipFile(archiveFile.toFile()).use { jdkZipFile ->
      val entry = jdkZipFile.getEntry("keymap.jar")
      assertThat(entry).isNotNull()
      assertThat(entry!!.size).isEqualTo(12805)
      assertThat(getCrc(jdkZipFile.getInputStream(entry).readAllBytes())).isEqualTo(entry.crc)
    }

    checkZip(archiveFile) { zipFile ->
      val entry = zipFile.getResource("keymap.jar")!!
      assertThat(entry).isNotNull()
      assertThat(entry.uncompressedSize).isEqualTo(12805)
      assertThat(entry.byteBuffer.remaining()).isEqualTo(12805)

      if (zipFile is HashMapZipFile) {
        assertThat(zipFile.getRawEntry("keymap.jar")!!.isCompressed).isTrue()
      }
    }
  }

  @Test
  fun `compress small`(@TempDir tempDir: Path) {
    doCompressTest(tempDir = tempDir, totalSize = 12 * 1024, minFileSize = 10 * 1024)
  }

  @Test
  fun `compress large`(@TempDir tempDir: Path) {
    doCompressTest(tempDir = tempDir, totalSize = 15 * 1024 * 1024, minFileSize = 10 * 1024 * 1024)
  }

  @Test
  fun `compress very large`(@TempDir tempDir: Path) {
    doCompressTest(tempDir = tempDir, totalSize = 300 * 1024 * 1024, minFileSize = 100 * 1024 * 1024)
  }

  private fun doCompressTest(tempDir: Path, totalSize: Int, minFileSize: Int) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val random = Random(42)
    val list = ArrayList<TestEntryItem>()
    var remainingSize = totalSize
    var fileIndex = 0
    while (remainingSize > 0) {
      val fileSize = if (remainingSize > minFileSize) random.nextInt(minFileSize, remainingSize) else remainingSize
      val item = TestEntryItem(name = "file-$fileIndex-$fileSize", size = fileSize)
      Files.newOutputStream(dir.resolve(item.name)).use {
        for (chunkSize in splitIntoChunks(size = fileSize, chunkSize = 512 * 1024)) {
          it.write(randomCompressibleBytes(chunkSize, random))
        }
      }
      list.add(item)
      remainingSize -= fileSize
      fileIndex++
    }

    val archiveFile = tempDir.resolve("archive.zip")
    zipWithCompression(archiveFile, mapOf(dir to ""))

    readZipFile(archiveFile) { name, dataProvider ->
      for (item in list) {
        if (name == item.name) {
          assertThat(dataProvider().remaining()).isEqualTo(item.size.toLong())
          return@readZipFile ZipEntryProcessorResult.STOP
        }
      }
      ZipEntryProcessorResult.CONTINUE
    }

    java.util.zip.ZipFile(archiveFile.toFile()).use { jdkZipFile ->
      for (item in list) {
        val entry = jdkZipFile.getEntry(item.name)
        assertThat(entry).isNotNull()
        assertThat(entry!!.size).isEqualTo(item.size.toLong())
        assertThat(computeZipEntryCrc32(jdkZipFile, entry)).isEqualTo(entry.crc)
      }
    }

    HashMapZipFile.load(archiveFile).use { zipFile ->
      for (item in list) {
        val entry = zipFile.getRawEntry(item.name)
        assertThat(entry).isNotNull()
        assertThat(entry!!.uncompressedSize).isEqualTo(item.size)
        assertThat(entry.isCompressed).isEqualTo(item.size > 8 * 1024)
      }
    }
  }

  @Test
  fun `large file`(@TempDir tempDir: Path) {
    doTestLargeFile(tempDir, useCrc = true)
  }

  @Test
  fun `large file without crc`(@TempDir tempDir: Path) {
    doTestLargeFile(tempDir, useCrc = false)
  }

  private fun doTestLargeFile(tempDir: Path, useCrc: Boolean) {
    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)
    val random = Random(42)
    Files.write(dir.resolve("largeFile1"), random.nextBytes(10 * 1024 * 1024))
    Files.write(dir.resolve("largeFile2"), random.nextBytes(1 * 1024 * 1024))
    Files.write(dir.resolve("largeFile3"), random.nextBytes(2 * 1024 * 1024))

    val archiveFile = tempDir.resolve("archive.zip")
    zip(targetFile = archiveFile, dirs = mapOf(dir to ""), useCrc = useCrc)

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

    java.util.zip.ZipFile(archiveFile.toFile()).use { jdkZipFile ->
      val entry = jdkZipFile.getEntry("largeFile1")
      assertThat(entry).isNotNull()
      assertThat(entry!!.size).isEqualTo(data.size.toLong())
      assertThat(entry.crc).isEqualTo(computeZipEntryCrc32(jdkZipFile, entry))
    }

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
}

// check both IKV- and non-IKV variants of an immutable zip file
internal fun checkZip(file: Path, checker: (ZipFile) -> Unit) {
  readZipFile(file) { name, dataProvider ->
    dataProvider()
    ZipEntryProcessorResult.CONTINUE
  }
  HashMapZipFile.load(file).use { zipFile ->
    checker(zipFile)
  }
  ImmutableZipFile.load(file).use { zipFile ->
    checker(zipFile)
  }
}

private fun getCrc(data: ByteArray): Long = CRC32().also { it.update(data) }.value

private fun randomCompressibleBytes(size: Int, random: Random): ByteArray {
  // leave the second half as zeros (ensure that is compressible)
  val bytes = ByteArray(size)
  random.nextBytes(bytes, 0, size / 2)
  return bytes
}

private fun writeTestJar(
  byteBuf: ByteBuf,
  testData1: Pair<String, ByteArray>,
  random: Random,
  tempDir: Path,
) {
  val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
  val dataWriter = ByteBufferDataWriter(byteBuf)
  val crc32 = CRC32()
  ZipArchiveOutputStream(dataWriter, ZipIndexWriter(packageIndexBuilder)).use { writer ->
    packageIndexBuilder.addFile("META-INF/pluginIcon.svg")
    writer.uncompressedData(testData1.first.toByteArray(), testData1.second, crc32)

    packageIndexBuilder.addFile("META-INF/pluginIcon_dark.svg")
    writer.uncompressedData("META-INF/pluginIcon_dark.svg".toByteArray(), random.nextBytes(2 * 4096), crc32)
  }

  val archiveFile = tempDir.resolve("innerArchive.zip")
  Files.write(archiveFile, dataWriter.toByteArray())

  HashMapZipFile.load(archiveFile).use { zipFile ->
    val entry = zipFile.getRawEntry(testData1.first)
    assertThat(entry).isNotNull()
    assertThat(entry!!.isCompressed).isFalse()
    assertThat(entry.getData(zipFile)).isEqualTo(testData1.second)
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

private fun computeZipEntryCrc32(zipFile: java.util.zip.ZipFile, entry: ZipEntry): Long {
  val crc = CRC32()
  val buffer = ByteArray(8192)
  zipFile.getInputStream(entry).use { inputStream ->
    var bytesRead: Int
    while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
      crc.update(buffer, 0, bytesRead)
    }
  }
  return crc.value
}

internal class TestEntryItem(
  @JvmField val size: Int,
  @JvmField val name: String,
)

internal suspend fun createLargeArchive(size: Int, tempDir: Path, minFileSize: Int = 0, maxFileSize: Int = 32): Pair<List<String>, Path> {
  val (dir, list) = createDirOnDisk(tempDir, size, minFileSize, maxFileSize)
  val archiveFile = tempDir.resolve("archive.zip")
  zipWithPackageIndex(archiveFile, dir)
  return Pair(list, archiveFile)
}

internal suspend fun createDirOnDisk(tempDir: Path, size: Int, minFileSize: Int, maxFileSize: Int): Pair<Path, List<String>> {
  val random = Random(42)

  val dir = tempDir.resolve("dir")
  Files.createDirectories(dir)
  val list = ArrayList<String>(size)

  withContext(Dispatchers.IO) {
    for (i in 0..size) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      launch {
        Files.write(dir.resolve(name), random.nextBytes(random.nextInt(minFileSize, maxFileSize)))
      }
    }
  }
  return Pair(dir, list)
}
