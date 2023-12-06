// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.copyRecursively
import com.intellij.util.lang.ImmutableZipFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.zip
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.random.Random

private val testDataPath: Path
  get() = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "plugins/reorderJars")

class ReorderJarsTest {
  @RegisterExtension
  @JvmField
  val fs = InMemoryFsExtension()

  @Test
  fun `keep all dirs with resources`() {
    // check that not only the immediate parent of resource file is preserved, but also any dir in a path
    val random = Random(42)

    val rootDir = fs.root.resolve("dir")
    val dir = rootDir.resolve("dir2/dir3")
    Files.createDirectories(dir)
    Files.write(dir.resolve("resource.txt"), random.nextBytes(random.nextInt(128)))

    val dir2 = rootDir.resolve("anotherDir")
    Files.createDirectories(dir2)
    Files.write(dir2.resolve("resource2.txt"), random.nextBytes(random.nextInt(128)))

    val archiveFile = fs.root.resolve("archive.jar")
    zip(archiveFile, mapOf(rootDir to ""))

    ImmutableZipFile.load(archiveFile).use { zipFile ->
      assertThat((zipFile as ImmutableZipFile).resourcePackages).isNotEmpty()
    }

    runBlocking {
      doReorderJars(mapOf(archiveFile to emptyList()), archiveFile.parent)
    }
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
    val dir = tempDir.resolve("dir")
    testDataPath.copyRecursively(dir)

    val annotationJar = dir.resolve("annotations.jar")

    runBlocking {
      doReorderJars(readClassLoadingLog(dir.resolve("order.txt").inputStream(), dir), dir)
    }
    val files = Files.newDirectoryStream(dir).use { it.toList() }
    assertThat(files).isNotNull()
    val names = getNamesInPhysicalOrder(annotationJar)
    assertThat(names.subList(0, 3)).containsExactly(
      "org/jetbrains/annotations/Nullable.class",
      "org/jetbrains/annotations/NotNull.class",
      "META-INF/MANIFEST.MF"
    )
  }

  @Test
  fun testPluginXml(@TempDir tempDir: Path) {
    val dir = tempDir.resolve("dir")
    testDataPath.copyRecursively(dir)
    runBlocking {
      doReorderJars(sourceToNames = readClassLoadingLog(classLoadingLog = dir.resolve("zkmOrder.txt").inputStream(), rootDir = dir),
                    sourceDir = dir)
    }
    assertThat(getNamesInPhysicalOrder(dir.resolve("zkm.jar")).first()).isEqualTo("META-INF/plugin.xml")
  }
}

private suspend fun doReorderJars(sourceToNames: Map<Path, List<String>>, sourceDir: Path) {
  withContext(Dispatchers.IO) {
    for ((jarFile, orderedNames) in sourceToNames) {
      if (Files.notExists(jarFile)) {
        Span.current().addEvent("cannot find jar", Attributes.of(AttributeKey.stringKey("file"), sourceDir.relativize(jarFile).toString()))
        continue
      }

      launch {
        reorderJar(jarFile = jarFile, orderedNames = orderedNames)
      }
    }
  }
}

private fun getNamesInPhysicalOrder(file: Path): MutableList<String> {
  // read entries in physical order
  val names = mutableListOf<String>()
  readZipFile(file) { name, _ ->
    names.add(name)
  }
  return names
}
