// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.delete
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Assume
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

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
    os = "windows",
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
  override fun setUp() {
    super.setUp()
    service<JdkInstallerStore>().loadState(JdkInstallerState())
  }

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

  private val mockWSL = object: WSLDistributionForJdkInstaller {
    override fun getWslPath(path: Path): String = path.toString()

    override fun executeOnWsl(command: List<String>, dir: String, timeout: Int): ProcessOutput {
      val cmd = GeneralCommandLine(command)
      cmd.workDirectory = File(dir)
      return CapturingProcessHandler(cmd).runProcess(timeout)
    }
  }

  private val mockWSLInstaller = object: JdkInstallerBase() {
    override fun wslDistributionFromPath(targetDir: Path) = mockWSL
    override fun defaultInstallDir(): Path = error("Must not call")
  }

  fun `test reuse pending jdks`() {
    val home1 = createTempDir("h2342341").toPath()
    val home2 = createTempDir("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)

    Assert.assertTrue(p1.toString().startsWith("PendingJdkRequest"))
    Assert.assertSame(p1, p2)
  }

  fun `test no reuse pending different jdks`() {
    val home1 = createTempDir("h2342341").toPath()
    val home2 = createTempDir("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockZip, home2)

    Assert.assertTrue(p1.toString().startsWith("PendingJdkRequest"))
    Assert.assertNotSame(p1, p2)
  }

  fun `test should not install the same JDK twice`() {
    val home1 = createTempDir("h2342341").toPath()
    val home2 = createTempDir("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    JdkInstaller.getInstance().installJdk(p1, ProgressIndicatorBase(), null)

    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)

    Assert.assertTrue(p1.toString().startsWith("PendingJdkRequest"))
    Assert.assertSame(p1, p2)

    //this must be fast
    repeat(1000) {
      val pN = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)
      JdkInstaller.getInstance().installJdk(pN, ProgressIndicatorBase(), null)
    }
  }

  fun `test re-use pending download`() {
    val eventsLog = mutableListOf<String>()
    val listener = object: JdkInstallerListener {
      override fun onJdkDownloadStarted(request: JdkInstallRequest, project: Project?) {
        synchronized(eventsLog) { eventsLog += "started $request"}
      }

      override fun onJdkDownloadFinished(request: JdkInstallRequest, project: Project?) {
        synchronized(eventsLog) { eventsLog += "completed $request"}
      }
    }
    ExtensionTestUtil.maskExtensions(ExtensionPointName.create<JdkInstallerListener>("com.intellij.jdkDownloader.jdkInstallerListener"), listOf(listener), testRootDisposable)

    val home1 = createTempDir("h2342341").toPath()
    val home2 = createTempDir("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)

    val t1 = thread { JdkInstaller.getInstance().installJdk(p1, ProgressIndicatorBase(), null) }
    val t2 = thread { JdkInstaller.getInstance().installJdk(p2, EmptyProgressIndicator(), null) }

    t1.join()
    t2.join()

    Assert.assertEquals("$eventsLog", 2, eventsLog.size)
    Assert.assertTrue("$eventsLog", eventsLog.first().startsWith("started"))
    Assert.assertTrue("$eventsLog", eventsLog.drop(1).first().startsWith("completed"))
  }

  fun `test unpacking tar gz`() = testUnpacking(mockTarGZ) {
    assertThat(installDir).isEqualTo(javaHome)
    assertThat(installDir.resolve("TheApp").resolve("FooBar.app").resolve("theApp")).isRegularFile()
    assertThat(installDir.resolve("TheApp").resolve("QPCV").resolve("ggg.txt")).isRegularFile()
  }

  fun `test unpacking tar gz in WSL`() {
    if (SystemInfo.isWindows) return

    testUnpacking(mockTarGZ.copy(os = "linux"), jdkInstaller = mockWSLInstaller) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("TheApp").resolve("FooBar.app").resolve("theApp")).isRegularFile()
      assertThat(installDir.resolve("TheApp").resolve("QPCV").resolve("ggg.txt")).isRegularFile()
    }
  }

  fun `test unpacking tar gz with root`() = testUnpacking(
    mockTarGZ.copy(
      packageRootPrefix = "TheApp",
      packageToBinJavaPrefix = "QPCV"
  )) {
    assertThat(javaHome.resolve("ggg.txt")).isRegularFile()

    assertThat((installDir.resolve("FooBar.app")).resolve("theApp")).isRegularFile()
    assertThat((installDir.resolve("QPCV")).resolve("ggg.txt")).isRegularFile()
  }

  fun `test unpacking tar gz with root WSL`() {
    if (SystemInfo.isWindows) return

    testUnpacking(
      mockTarGZ.copy(
        os = "linux",
        packageRootPrefix = "TheApp",
        packageToBinJavaPrefix = "QPCV"
      ),
      jdkInstaller = mockWSLInstaller) {
      assertThat(javaHome.resolve("ggg.txt")).isRegularFile()

      assertThat((installDir.resolve("FooBar.app")).resolve("theApp")).isRegularFile()
      assertThat((installDir.resolve("QPCV")).resolve("ggg.txt")).isRegularFile()
    }
  }

  fun `test unpacking tar gz cut dirs`() {
    testUnpacking(mockTarGZ.copy(packageRootPrefix = "TheApp/FooBar.app")) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("theApp")).isRegularFile()
      assertThat(installDir.resolve("ggg.txt")).doesNotExist()
    }
  }

  fun `test unpacking tar gz cut dirs WSL`() {
    if (SystemInfo.isWindows) return

    testUnpacking(mockTarGZ.copy(packageRootPrefix = "TheApp/FooBar.app", os = "linux"), jdkInstaller = mockWSLInstaller) {
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

  fun `test unpacking zip package path`() = testUnpacking(mockZip.copy(packageToBinJavaPrefix = "folder")) {
    assertThat(javaHome.resolve("readme2")).isDirectory()
    assertThat(javaHome.resolve("file")).isRegularFile()
    assertThat((installDir.resolve("folder")).resolve("readme2")).isDirectory()
    assertThat(installDir.resolve("folder").resolve("file")).isRegularFile()
  }

  fun `test unpacking zip package path WSL`() {
    if (SystemInfo.isWindows) return

    testUnpacking(mockZip.copy(packageToBinJavaPrefix = "folder", os = "linux"), mockWSLInstaller) {
      assertThat(javaHome.resolve("readme2")).isDirectory()
      assertThat(javaHome.resolve("file")).isRegularFile()
      assertThat((installDir.resolve("folder")).resolve("readme2")).isDirectory()
      assertThat(installDir.resolve("folder").resolve("file")).isRegularFile()
    }
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

  fun `test unpacking zip and prefix WSL`() {
    if (SystemInfo.isWindows) return

    expectsException {
      testUnpacking(
        mockZip.copy(
          os = "linux",
          packageRootPrefix = "folder/file"),
        mockWSLInstaller
      ) {
        assertThat(installDir.resolve("readme2")).doesNotExist()
        assertThat(installDir.resolve("folder").resolve("file")).doesNotExist()
      }
    }
  }

  private fun testUnpacking(item: JdkItem, jdkInstaller: JdkInstallerBase = JdkInstaller.getInstance(), resultDir: JdkInstallRequest.() -> Unit = { error("must not reach here") }) {
    val dir = Files.createTempDirectory("")
    try {
      val task = jdkInstaller.prepareJdkInstallationDirect(item, dir)
      jdkInstaller.installJdk(task, null, null)

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
