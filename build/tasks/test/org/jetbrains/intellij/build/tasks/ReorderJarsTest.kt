// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package org.jetbrains.intellij.build.tasks

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.lang.JarMemoryLoader
import com.intellij.util.lang.JdkZipFile
import org.apache.commons.compress.archivers.zip.ZipFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry

private val testDataPath: Path
  get() = Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "plugins/reorderJars")

class ReorderJarsTest {
  @JvmField
  @Rule
  val tempDir = TemporaryDirectory()

  @Test
  fun testReordering() {
    val path = testDataPath
    ZipFile("$path/annotations.jar").use { zipFile1 ->
      zipFile1.entries.toList()
    }

    val tempDir = tempDir.createDir()
    Files.createDirectories(tempDir)

    doReorderJars(readClassLoadingLog(path.resolve("order.txt"), path), path, tempDir, TaskTest.logger)
    val files = tempDir.toFile().listFiles()!!
    assertThat(files).isNotNull()
    assertThat(files).hasSize(1)
    val file = files[0].toPath()
    assertThat(file.fileName.toString()).isEqualTo("annotations.jar")
    var data: ByteArray
    ZipFile(file.toFile()).use { zipFile2 ->
      val entries = zipFile2.entriesInPhysicalOrder.toList()
      assertThat(entries[0].name).isEqualTo(SIZE_ENTRY)
      val entry = entries[1]
      data = zipFile2.getInputStream(entry).readNBytes(entry.size.toInt())
      assertThat(data).hasSize(548)
      assertThat(entry.name).isEqualTo("org/jetbrains/annotations/Nullable.class")
      assertThat(entries[2].name).isEqualTo("org/jetbrains/annotations/NotNull.class")
      assertThat(entries[3].name).isEqualTo("META-INF/MANIFEST.MF")
    }

    val loader = JdkZipFile(file, true, false).preload(file, null)
    assertThat(loader).isNotNull()
    val resource = loader!!.getResource("org/jetbrains/annotations/Nullable.class")
    assertThat(resource).isNotNull()
    val bytes = resource!!.getBytes()
    assertThat(bytes).hasSize(548)
    assertThat(data.contentEquals(bytes)).isTrue()
  }

  @Test
  fun testPluginXml() {
    val tempDir = tempDir.createDir()
    Files.createDirectories(tempDir)

    val path = testDataPath
    doReorderJars(readClassLoadingLog(path.resolve("zkmOrder.txt"), path), path, tempDir, TaskTest.logger)
    val files = tempDir.toFile().listFiles()!!
    assertThat(files).isNotNull()
    val file = files[0]
    assertThat(file.name).isEqualTo("zkm.jar")
    ZipFile(file).use { zipFile ->
      val entries: List<ZipEntry> = zipFile.entries.toList()
      assertThat(entries[0].name).isEqualTo(JarMemoryLoader.SIZE_ENTRY)
      assertThat(entries[1].name).isEqualTo("META-INF/plugin.xml")
    }
  }
}