// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.openapi.projectRoots.impl

import com.intellij.jdkDownloader.*
import com.intellij.testFramework.rules.TempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

class JdkDownloaderTest {
  @get:Rule
  val fsRule = TempDirectory()

  private fun jdkItemForTest(url: String,
                             packageType: JdkPackageType,
                             size: Long,
                             sha256: String,
                             cutDirs: Int = 0) = JdkItem(
    JdkProduct("Vendor", null, null),
    false,
    123,
    "123.123",
    null,
    null,
    "jetbrains-hardware",
    packageType,
    url,
    sha256,
    size,
    10 * size,
    cutDirs,
    "",
    url.split("/").last(),
    url.split("/").last().removeSuffix(".tar.gz").removeSuffix(".zip")
  )

  private val mockTarGZ = jdkItemForTest(packageType = JdkPackageType.TAR_GZ,
                                         url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.tar.gz",
                                         size = 249,
                                         sha256 = "ffc8825d96e3f89cb4a8ca64b9684c37f55d6c5bd54628ebf984f8282f8a59ff"
  )
  private val mockZip = jdkItemForTest(packageType = JdkPackageType.ZIP,
                                       url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.zip",
                                       size = 604,
                                       sha256 = "1cf15536c1525f413190fd53243f343511a17e6ce7439ccee4dc86f0d34f9e81")

  @Test
  fun `default model can be downloaded and parsed`() {
    val data = JdkListDownloader.downloadModel(null)
    Assert.assertTrue(data.isNotEmpty())
  }

  @Test
  fun `unpacking tar gz`() = testUnpacking(mockTarGZ) { dir ->
    assertThat(File(dir, "TheApp/FooBar.app/theApp")).isFile()
    assertThat(File(dir, "TheApp/QPCV/ggg.txt")).isFile()
  }

  @Test
  fun `unpacking tar gz cut dirs`() = testUnpacking(mockTarGZ.copy(unpackCutDirs = 2)) { dir ->
    assertThat(File(dir, "theApp")).isFile()
    assertThat(File(dir, "ggg.txt")).isFile()
  }

  @Test
  fun `unpacking tar gz cut dirs and prefix`() = testUnpacking(
    mockTarGZ.copy(
      unpackCutDirs = 2,
      unpackPrefixFilter = "TheApp/FooBar.app")
  ) { dir ->
    assertThat(File(dir, "theApp")).isFile()
    assertThat(File(dir, "ggg.txt")).doesNotExist()
  }

  @Test(expected = Exception::class)
  fun `unpacking targz invalid size`() = testUnpacking(mockTarGZ.copy(archiveSize = 234234)) { dir ->
    assertThat(File(dir, "TheApp/FooBar.app/theApp")).isFile()
    assertThat(File(dir, "TheApp/QPCV/ggg.txt")).isFile()
  }

  @Test(expected = Exception::class)
  fun `unpacking targz invalid checksum`() = testUnpacking(mockTarGZ.copy(sha256 = "234234")) { dir ->
    assertThat(File(dir, "TheApp/FooBar.app/theApp")).isFile()
    assertThat(File(dir, "TheApp/QPCV/ggg.txt")).isFile()
  }

  @Test
  fun `unpacking zip`() = testUnpacking(mockZip) { dir ->
    assertThat(File(dir, "folder/readme2")).isDirectory()
    assertThat(File(dir, "folder/file")).isFile()
  }

  @Test(expected = Exception::class)
  fun `unpacking zip invalid size`() = testUnpacking(mockZip.copy(archiveSize = 234)) { dir ->
    assertThat(File(dir, "folder/readme2")).isDirectory()
    assertThat(File(dir, "folder/file")).isFile()
  }

  @Test(expected = Exception::class)
  fun `unpacking zip invalid checksum`() = testUnpacking(mockZip.copy(sha256 = "234")) { dir ->
    assertThat(File(dir, "folder/readme2")).isDirectory()
    assertThat(File(dir, "folder/file")).isFile()
  }

  @Test
  fun `unpacking zip cut dirs and wrong prefix`() = testUnpacking(
    mockZip.copy(
      unpackCutDirs = 1,
      unpackPrefixFilter = "wrong")
  ) { dir ->
    assertThat(File(dir, "folder/readme2")).doesNotExist()
    assertThat(File(dir, "folder/file")).doesNotExist()
  }

  @Test
  fun `unpacking zip cut dirs and prefix`() = testUnpacking(
    mockZip.copy(
      unpackCutDirs = 1,
      unpackPrefixFilter = "folder")
  ) { dir ->
    assertThat(File(dir, "readme2")).isDirectory()
    assertThat(File(dir, "file")).isFile()
  }

  @Test
  fun `unpacking zip and prefix`() = testUnpacking(
    mockZip.copy(
      unpackCutDirs = 0,
      unpackPrefixFilter = "folder/file")
  ) { dir ->
    assertThat(File(dir, "readme2")).doesNotExist()
    assertThat(File(dir, "folder/file")).isFile()
  }

  private inline fun testUnpacking(item: JdkItem, resultDir: (File) -> Unit) {
    val dir = fsRule.newFolder()
    JdkInstaller.installJdk(item, dir.absolutePath, null)
    resultDir(dir)
  }
}
