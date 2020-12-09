// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.lang.zip.ImmutableZipFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import kotlin.random.Random

@Suppress("UsePropertyAccessSyntax")
class ZipTest {
  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test
  fun `do not compress jars and images`() {
    val random = Random(42)

    val dir = fsRule.fs.getPath("/dir")
    Files.createDirectories(dir)
    val fileDescriptors = listOf(Entry("lib.jar", true), Entry("lib.zip", true), Entry("image.png", true), Entry("scalable-image.svg", true), Entry("readme.txt", true))
    for (entry in fileDescriptors) {
      Files.write(dir.resolve(entry.path), random.nextBytes(42))
    }

    val archiveFile = fsRule.fs.getPath("/archive.zip")
    zipForWindows(archiveFile, listOf(dir))

    val zipFile = ImmutableZipFile.load(archiveFile)
    for (entry in fileDescriptors) {
      assertThat(zipFile.getEntry(entry.path).method)
        .describedAs(entry.path)
        .isEqualTo(if (entry.isCompressed) ZipEntry.DEFLATED else ZipEntry.STORED)
    }
  }

  @Test
  fun `read zip file with more than 65K entries`() {
    val random = Random(42)

    val dir = fsRule.fs.getPath("/dir")
    Files.createDirectories(dir)
    val list = mutableListOf<String>()
    for (i in 0..(Short.MAX_VALUE * 2)) {
      val name = "entry-item${random.nextInt()}-$i"
      list.add(name)
      Files.write(dir.resolve(name), random.nextBytes(random.nextInt(128)))
    }

    val archiveFile = fsRule.fs.getPath("/archive.zip")
    zipForWindows(archiveFile, listOf(dir))

    val zipFile = ImmutableZipFile.load(archiveFile)
    for (name in list) {
      assertThat(zipFile.getEntry(name)).isNotNull()
    }
  }
}

private class Entry(val path: String, val isCompressed: Boolean)