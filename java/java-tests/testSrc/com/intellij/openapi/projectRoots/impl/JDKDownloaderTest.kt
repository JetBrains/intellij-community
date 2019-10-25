// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.testFramework.rules.TempDirectory
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

class JDKDownloaderTest {
  @get:Rule
  val fsRule = TempDirectory()

  @Test
  fun `model can be downloaded and parsed`() {
    val data = JDKListDownloader.downloadModel(null)
    Assert.assertTrue(data.isNotEmpty())
  }

  val vendor = JDKVendor(("mock"))
  val mockTarGZ = JDKDownloadItem(vendor = vendor, version = "1231", arch = "x", archiveType = "targz",
                                  url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.tar.gz",
                                  size = 249,
                                  sha256 = "ffc8825d96e3f89cb4a8ca64b9684c37f55d6c5bd54628ebf984f8282f8a59ff")
  val mockZip = JDKDownloadItem(vendor = vendor, version = "1231", arch = "x", archiveType = "zip",
                                url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.zip",
                                size = 604,
                                sha256 = "1cf15536c1525f413190fd53243f343511a17e6ce7439ccee4dc86f0d34f9e81")

  @Test
  fun `unpacking targz`() = testUnpacking(mockTarGZ) {dir ->
    Assert.assertTrue(File(dir, "TheApp/FooBar.app/theApp").isFile)
    Assert.assertTrue(File(dir, "TheApp/QPCV/ggg.txt").isFile)
  }

  @Test
  fun `unpacking zip`() = testUnpacking(mockZip) {dir ->
    Assert.assertTrue(File(dir, "folder/readme2").isDirectory)
    Assert.assertTrue(File(dir, "folder/file").isFile)
  }

  private fun testUnpacking(item: JDKDownloadItem, resultDir: (File) -> Unit) {
    val dir = fsRule.newFolder()
    JDKInstaller.installJDK(item, dir, null)
    resultDir(dir)
  }
}
