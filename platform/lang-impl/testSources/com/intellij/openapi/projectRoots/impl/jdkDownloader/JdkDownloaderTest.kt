// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.idea.TestFor
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.delete
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import java.nio.file.Files

internal fun jdkItemForTest(url: String,
                            packageType: JdkPackageType,
                            size: Long,
                            sha256: String,
                            prefix: String = "",
                            packageToHomePrefix: String = ""): JdkItem {
  val product = JdkProduct(vendor = "Vendor", product = null, flavour = null)
  return JdkItem(
    product,
    isDefaultItem = false,
    isVisibleOnUI = true,
    jdkMajorVersion = 123,
    jdkVersion = "123.123",
    jdkVendorVersion = null,
    suggestedSdkName = "suggested",
    arch = "jetbrains-hardware",
    packageType = packageType,
    url = url,
    sha256 = sha256,
    archiveSize = size,
    unpackedSize = 10 * size,
    packageRootPrefix = prefix,
    packageToBinJavaPrefix = packageToHomePrefix,
    archiveFileName = url.split("/").last(),
    installFolderName = url.split("/").last().removeSuffix(".tar.gz").removeSuffix(".zip"),
    sharedIndexAliases = listOf(),
    saveToFile = {}
  )
}

class JdkDownloaderTest : LightPlatformTestCase() {
  private val mockTarGZ = jdkItemForTest(packageType = JdkPackageType.TAR_GZ,
                                         url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.tar.gz",
                                         size = 249,
                                         sha256 = "ffc8825d96e3f89cb4a8ca64b9684c37f55d6c5bd54628ebf984f8282f8a59ff"
  )

  private val mockTarGZ2 = jdkItemForTest(packageType = JdkPackageType.TAR_GZ,
                                          url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data-2.tar.gz",
                                          size = 318,
                                          sha256 = "963af2c1578a376340f60c5adabf217f59006cfc8b2b3fc97edda2e90c0295e2"
  )
  private val mockZip = jdkItemForTest(packageType = JdkPackageType.ZIP,
                                       url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.zip",
                                       size = 604,
                                       sha256 = "1cf15536c1525f413190fd53243f343511a17e6ce7439ccee4dc86f0d34f9e81")

  fun `test unpacking tar gz`() = testUnpacking(mockTarGZ) {
    assertThat(installDir).isEqualTo(javaHome)
    assertThat(installDir.resolve("TheApp").resolve("FooBar.app").resolve("theApp")).isRegularFile()
    assertThat(installDir.resolve("TheApp").resolve("QPCV").resolve("ggg.txt")).isRegularFile()
  }

  @TestFor(issues = ["IDEA-231609"])
  fun `test unpacking tar gz with root`() = testUnpacking(
    mockTarGZ.copy(
      packageRootPrefix = "TheApp",
      packageToBinJavaPrefix = "QPCV"
  )) {
    assertThat(javaHome.resolve("ggg.txt")).isRegularFile()

    assertThat((installDir.resolve("FooBar.app")).resolve("theApp")).isRegularFile()
    assertThat((installDir.resolve("QPCV")).resolve("ggg.txt")).isRegularFile()
  }

  fun `test unpacking tar gz cut dirs`() {
    testUnpacking(mockTarGZ.copy(packageRootPrefix = "TheApp/FooBar.app")) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("theApp")).isRegularFile()
      assertThat(installDir.resolve("ggg.txt")).doesNotExist()
    }
  }

  fun `test unpacking tar gz cut dirs 2`() {
    Assume.assumeTrue(SystemInfo.isMac || SystemInfo.isLinux)

    testUnpacking(mockTarGZ2.copy(packageRootPrefix = "this/jdk")) {
      assertThat(installDir.resolve("bin").resolve("java")).isRegularFile()
      assertThat((installDir.resolve("bin")).resolve("javac")).isRegularFile()
      assertThat(installDir.resolve("file")).isRegularFile()
      assertThat(((installDir.resolve("bin")).resolve("symlink"))).isSymbolicLink().hasSameTextualContentAs(installDir.resolve("file"))
    }
  }

  fun `test unpacking tar gz cut dirs complex prefix`() = testUnpacking(
    mockTarGZ.copy(packageRootPrefix = "./TheApp/FooBar.app")
  ) {
    assertThat(installDir).isEqualTo(javaHome)
    assertThat(installDir.resolve("theApp")).isRegularFile()
    assertThat(installDir.resolve("ggg.txt")).doesNotExist()
  }

  fun `test unpacking tar gz cut dirs and prefix`() = testUnpacking(
    mockTarGZ.copy(packageRootPrefix = "TheApp/FooBar.app")
  ) {
    assertThat(installDir.resolve("theApp")).isRegularFile()
    assertThat(installDir.resolve("ggg.txt")).doesNotExist()
  }

  fun `test unpacking targz invalid size`() = expectsException {
    testUnpacking(mockTarGZ.copy(archiveSize = 234234))
  }

  fun `test unpacking targz invalid checksum`() = expectsException { testUnpacking(mockTarGZ.copy(sha256 = "234234")) }

  fun `test unpacking zip`() = testUnpacking(mockZip) {
    assertThat(installDir).isEqualTo(javaHome)
    assertThat((installDir.resolve("folder")).resolve("readme2")).isDirectory()
    assertThat(installDir.resolve("folder").resolve("file")).isRegularFile()
  }

  @TestFor(issues = ["IDEA-231609"])
  fun `test unpacking zip package path`() = testUnpacking(mockZip.copy(packageToBinJavaPrefix = "folder")) {
    assertThat(javaHome.resolve("readme2")).isDirectory()
    assertThat(javaHome.resolve("file")).isRegularFile()
    assertThat((installDir.resolve("folder")).resolve("readme2")).isDirectory()
    assertThat(installDir.resolve("folder").resolve("file")).isRegularFile()
  }

  fun `test unpacking zip invalid size`() = expectsException {
    testUnpacking(mockZip.copy(archiveSize = 234))
  }

  fun `test unpacking zip invalid checksum`() = expectsException { testUnpacking(mockZip.copy(sha256 = "234")) }

  fun `test unpacking zip cut dirs and wrong prefix`() = testUnpacking(
    mockZip.copy(
      packageRootPrefix = "wrong")
  ) {
    assertThat(installDir.resolve("folder/readme2")).doesNotExist()
    assertThat(installDir.resolve("folder/file")).doesNotExist()
  }

  fun `test unpacking zip cut dirs and prefix`() {
    testUnpacking(mockZip.copy(packageRootPrefix = "folder")) {
      assertThat(installDir.resolve("readme2")).isDirectory()
      assertThat(installDir.resolve("file")).isRegularFile()
    }
  }

  fun `test unpacking zip and prefix`() = testUnpacking(
    mockZip.copy(
      packageRootPrefix = "folder/file")
  ) {
    assertThat(installDir.resolve("readme2")).doesNotExist()
    assertThat(installDir.resolve("folder").resolve("file")).doesNotExist()
  }

  private fun testUnpacking(item: JdkItem, resultDir: JdkInstallRequest.() -> Unit = { error("must not reach here") }) {
    val dir = Files.createTempDirectory("")
    try {
      val task = JdkInstaller.getInstance().prepareJdkInstallation(item, dir)
      JdkInstaller.getInstance().installJdk(task, null, null)

      assertThat(task.installDir).isDirectory()
      assertThat(task.javaHome).isDirectory()

      assertThat(dir).isEqualTo(task.installDir)

      LOG.debug("Unpacked files:")
      dir.toFile().walkTopDown().forEach {
        LOG.debug("  <install dir>${it.path.removePrefix(dir.toString())}")
      }

      task.resultDir()
    }
    finally {
      dir.delete()
    }
  }

  private fun expectsException(action: () -> Unit) {
    try {
      action()
      error("Exception was expected")
    }
    catch (t: Exception) {
      return
    }
  }
}
