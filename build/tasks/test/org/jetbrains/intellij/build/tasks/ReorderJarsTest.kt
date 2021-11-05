// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package org.jetbrains.intellij.build.tasks

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.Murmur3_32Hash
import com.intellij.util.io.inputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.io.RW_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.zip
import org.junit.Rule
import org.junit.Test
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import kotlin.random.Random

private val testDataPath: Path
  get() = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "plugins/reorderJars")

class ReorderJarsTest {
  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @JvmField
  @Rule
  val tempDir = TemporaryDirectory()

  @Test
  fun `dir to create`() {
    val packageIndexBuilder = PackageIndexBuilder()
    packageIndexBuilder.addFile("tsMeteorStubs/meteor-v1.3.1.d.ts")
    assertThat(packageIndexBuilder._getDirsToCreate()).containsExactlyInAnyOrder("tsMeteorStubs")

    val file = fsRule.fs.getPath("/f")
    Files.createDirectories(file.parent)
    FileChannel.open(file, RW_CREATE_NEW).use {
      packageIndexBuilder.writePackageIndex(ZipFileWriter(it, deflater = null))
    }
    assertThat(packageIndexBuilder.resourcePackageHashSet)
      .containsExactlyInAnyOrder(0, Murmur3_32Hash.MURMUR3_32.hashString("tsMeteorStubs", 0, "tsMeteorStubs".length))
  }

  @Test
  fun `keep all dirs with resources`() {
    // check that not only immediate parent of resource file is preserved, but also any dir in a path
    val random = Random(42)

    val rootDir = fsRule.fs.getPath("/dir")
    val dir = rootDir.resolve("dir2/dir3")
    Files.createDirectories(dir)
    Files.write(dir.resolve("resource.txt"), random.nextBytes(random.nextInt(128)))

    val dir2 = rootDir.resolve("anotherDir")
    Files.createDirectories(dir2)
    Files.write(dir2.resolve("resource2.txt"), random.nextBytes(random.nextInt(128)))

    val archiveFile = fsRule.fs.getPath("/archive.jar")
    zip(archiveFile, mapOf(rootDir to ""), compress = false, addDirEntries = true)

    doReorderJars(mapOf(archiveFile to emptyList()), archiveFile.parent, archiveFile.parent)
    ZipFile(Files.newByteChannel(archiveFile)).use { zipFile ->
      assertThat(zipFile.entriesInPhysicalOrder.asSequence().map { it.name }.sorted().joinToString(separator = "\n")).isEqualTo("""
        __packageIndex__
        anotherDir/
        anotherDir/resource2.txt
        dir2/
        dir2/dir3/
        dir2/dir3/resource.txt
      """.trimIndent())
    }
  }

  @Test
  fun testReordering() {
    val path = testDataPath
    ZipFile("$path/annotations.jar").use { zipFile1 ->
      zipFile1.entries.toList()
    }

    val tempDir = tempDir.createDir()
    Files.createDirectories(tempDir)

    doReorderJars(readClassLoadingLog(path.resolve("order.txt").inputStream(), path, "idea.jar"), path, tempDir)
    val files = tempDir.toFile().listFiles()!!
    assertThat(files).isNotNull()
    assertThat(files).hasSize(1)
    val file = files[0].toPath()
    assertThat(file.fileName.toString()).isEqualTo("annotations.jar")
    var data: ByteArray
    ZipFile(Files.newByteChannel(file)).use { zipFile2 ->
      val entries = zipFile2.entriesInPhysicalOrder.toList()
      val entry = entries[0]
      data = zipFile2.getInputStream(entry).readNBytes(entry.size.toInt())
      assertThat(data).hasSize(548)
      assertThat(entry.name).isEqualTo("org/jetbrains/annotations/Nullable.class")
      assertThat(entries[1].name).isEqualTo("org/jetbrains/annotations/NotNull.class")
      assertThat(entries[2].name).isEqualTo("META-INF/MANIFEST.MF")
    }
  }

  @Test
  fun testPluginXml() {
    val tempDir = tempDir.createDir()
    Files.createDirectories(tempDir)

    val path = testDataPath
    doReorderJars(readClassLoadingLog(path.resolve("zkmOrder.txt").inputStream(), path, "idea.jar"), path, tempDir)
    val files = tempDir.toFile().listFiles()!!
    assertThat(files).isNotNull()
    val file = files[0]
    assertThat(file.name).isEqualTo("zkm.jar")
    ZipFile(file).use { zipFile ->
      val entries: List<ZipEntry> = zipFile.entries.toList()
      assertThat(entries.last().name).isEqualTo(PACKAGE_INDEX_NAME)
      assertThat(entries.first().name).isEqualTo("META-INF/plugin.xml")
    }
  }
}