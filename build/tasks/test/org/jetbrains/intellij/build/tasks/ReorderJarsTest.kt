// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package org.jetbrains.intellij.build.tasks

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.inputStream
import com.intellij.util.lang.ImmutableZipFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.apache.commons.compress.archivers.zip.ZipFile
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.io.zip
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.zip.ZipEntry
import kotlin.random.Random

private val testDataPath: Path
  get() = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "plugins/reorderJars")

class ReorderJarsTest {
  @RegisterExtension
  @JvmField
  val fs = InMemoryFsExtension()

  @Test
  fun `keep all dirs with resources`() {
    // check that not only immediate parent of resource file is preserved, but also any dir in a path
    val random = Random(42)

    val rootDir = fs.root.resolve("dir")
    val dir = rootDir.resolve("dir2/dir3")
    Files.createDirectories(dir)
    Files.write(dir.resolve("resource.txt"), random.nextBytes(random.nextInt(128)))

    val dir2 = rootDir.resolve("anotherDir")
    Files.createDirectories(dir2)
    Files.write(dir2.resolve("resource2.txt"), random.nextBytes(random.nextInt(128)))

    val archiveFile = fs.root.resolve("archive.jar")
    zip(archiveFile, mapOf(rootDir to ""), compress = false, addDirEntries = true)

    doReorderJars(mapOf(archiveFile to emptyList()), archiveFile.parent, archiveFile.parent)
    ImmutableZipFile.load(archiveFile).use { zipFile ->
      assertThat(zipFile.getResource("anotherDir")).isNotNull()
      assertThat(zipFile.getResource("dir2")).isNotNull()
      assertThat(zipFile.getResource("dir2/dir3")).isNotNull()
    }

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      assertThat(zipFile.getResource("anotherDir")).isNotNull()
    }
  }

  @Test
  fun testReordering(@TempDir tempDir: Path) {
    val path = testDataPath
    ZipFile("$path/annotations.jar").use { zipFile1 ->
      zipFile1.entries.toList()
    }

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
  fun testPluginXml(@TempDir tempDir: Path) {
    Files.createDirectories(tempDir)

    val path = testDataPath
    doReorderJars(readClassLoadingLog(path.resolve("zkmOrder.txt").inputStream(), path, "idea.jar"), path, tempDir)
    val files = tempDir.toFile().listFiles()!!
    assertThat(files).isNotNull()
    val file = files[0]
    assertThat(file.name).isEqualTo("zkm.jar")
    ZipFile(file).use { zipFile ->
      val entries: List<ZipEntry> = zipFile.entries.toList()
      assertThat(entries.first().name).isEqualTo("META-INF/plugin.xml")
    }
  }
}

private fun doReorderJars(sourceToNames: Map<Path, List<String>>, sourceDir: Path, targetDir: Path) {
  ForkJoinTask.invokeAll(sourceToNames.mapNotNull { (jarFile, orderedNames) ->
    if (Files.notExists(jarFile)) {
      Span.current().addEvent("cannot find jar", Attributes.of(AttributeKey.stringKey("file"), sourceDir.relativize(jarFile).toString()))
      return@mapNotNull null
    }

    task(tracer.spanBuilder("reorder jar")
           .setAttribute("file", sourceDir.relativize(jarFile).toString())) {
      reorderJar(jarFile, orderedNames, if (targetDir == sourceDir) jarFile else targetDir.resolve(sourceDir.relativize(jarFile)))
    }
  })
}