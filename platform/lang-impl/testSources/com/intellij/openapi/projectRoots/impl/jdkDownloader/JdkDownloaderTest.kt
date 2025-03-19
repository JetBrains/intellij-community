// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

class JdkDownloaderTest : BareTestFixtureTestCase() {
  private val LOG = logger<JdkDownloaderTest>()

  @Rule @JvmField val tempDir = TempDirectory()

  @Before fun setUpService() {
    service<JdkInstallerStore>().loadState(JdkInstallerState())
  }

  private val mockTarGZ = jdkItemForTest(packageType = JdkPackageType.TAR_GZ,
                                         url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.tar.gz",
                                         size = 249,
                                         sha256 = "ffc8825d96e3f89cb4a8ca64b9684c37f55d6c5bd54628ebf984f8282f8a59ff")

  private val mockTarGZ2 = jdkItemForTest(packageType = JdkPackageType.TAR_GZ,
                                          url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data-2.tar.gz",
                                          size = 318,
                                          sha256 = "963af2c1578a376340f60c5adabf217f59006cfc8b2b3fc97edda2e90c0295e2")

  private val mockZip = jdkItemForTest(packageType = JdkPackageType.ZIP,
                                       url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.zip",
                                       size = 604,
                                       sha256 = "1cf15536c1525f413190fd53243f343511a17e6ce7439ccee4dc86f0d34f9e81")

  private val mockWSL = object: OsAbstractionForJdkInstaller.Wsl {
    override val d: WSLDistribution
      get() = TODO("Not yet implemented")

    override fun getPath(path: Path): String = path.toString()

    override fun execute(command: List<String>, dir: String, timeout: Int): ProcessOutput {
      val processHandler = CapturingProcessHandler(GeneralCommandLine(command).withWorkingDirectory(Path.of(dir)));
      return processHandler.runProcess(timeout)
    }
  }

  private val mockWSLInstaller = object: JdkInstallerBase() {
    override fun wslDistributionFromPath(targetDir: Path) = mockWSL
    override fun defaultInstallDir(osAbstractionForJdkInstaller: OsAbstractionForJdkInstaller?): Path = error("Must not call")
  }

  @Test fun `test reuse pending JDKs`() {
    val home1 = tempDir.newDirectory("h2342341").toPath()
    val home2 = tempDir.newDirectory("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)

    assertTrue(p1.toString().startsWith("PendingJdkRequest"))
    assertSame(p1, p2)
  }

  @Test fun `test no reuse pending different JDKs`() {
    val home1 = tempDir.newDirectory("h2342341").toPath()
    val home2 = tempDir.newDirectory("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockZip, home2)

    assertTrue(p1.toString().startsWith("PendingJdkRequest"))
    assertNotSame(p1, p2)
  }

  @Test fun `test should not install the same JDK twice`() {
    val home1 = tempDir.newDirectory("h2342341").toPath()
    val home2 = tempDir.newDirectory("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    JdkInstaller.getInstance().installJdk(p1, ProgressIndicatorBase(), null)

    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)

    assertTrue(p1.toString().startsWith("PendingJdkRequest"))
    assertSame(p1, p2)

    //this must be fast
    repeat(1000) {
      val pN = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)
      JdkInstaller.getInstance().installJdk(pN, ProgressIndicatorBase(), null)
    }
  }

  @Test fun `test re-use pending download`() {
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

    val home1 = tempDir.newDirectory("h2342341").toPath()
    val home2 = tempDir.newDirectory("234234h2").toPath()

    val p1 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home1)
    val p2 = JdkInstaller.getInstance().prepareJdkInstallation(mockTarGZ, home2)

    val t1 = thread { JdkInstaller.getInstance().installJdk(p1, ProgressIndicatorBase(), null) }
    val t2 = thread { JdkInstaller.getInstance().installJdk(p2, EmptyProgressIndicator(), null) }

    t1.join()
    t2.join()

    assertEquals("$eventsLog", 2, eventsLog.size)
    assertTrue("$eventsLog", eventsLog.first().startsWith("started"))
    assertTrue("$eventsLog", eventsLog.drop(1).first().startsWith("completed"))
  }

  @Test fun `test unpacking tar_gz`() =
    testUnpacking(mockTarGZ) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("TheApp/FooBar.app/theApp")).isRegularFile()
      assertThat(installDir.resolve("TheApp/QPCV/ggg.txt")).isRegularFile()
    }

  @Test fun `test unpacking tar_gz in WSL`() {
    IoTestUtil.assumeUnix()

    testUnpacking(mockTarGZ.copy(os = "linux"), jdkInstaller = mockWSLInstaller) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("TheApp/FooBar.app/theApp")).isRegularFile()
      assertThat(installDir.resolve("TheApp/QPCV/ggg.txt")).isRegularFile()
    }
  }

  @Test fun `test unpacking tar_gz with root`() =
    testUnpacking(mockTarGZ.copy(packageRootPrefix = "TheApp", packageToBinJavaPrefix = "QPCV")) {
      assertThat(javaHome.resolve("ggg.txt")).isRegularFile()
      assertThat(installDir.resolve("FooBar.app/theApp")).isRegularFile()
      assertThat(installDir.resolve("QPCV/ggg.txt")).isRegularFile()
    }

  @Test fun `test unpacking tar_gz with root WSL`() {
    IoTestUtil.assumeUnix()

    testUnpacking(
      mockTarGZ.copy(os = "linux", packageRootPrefix = "TheApp", packageToBinJavaPrefix = "QPCV"),
      jdkInstaller = mockWSLInstaller
    ) {
      assertThat(javaHome.resolve("ggg.txt")).isRegularFile()
      assertThat(installDir.resolve("FooBar.app/theApp")).isRegularFile()
      assertThat(installDir.resolve("QPCV/ggg.txt")).isRegularFile()
    }
  }

  @Test fun `test unpacking tar_gz cut dirs`() =
    testUnpacking(mockTarGZ.copy(packageRootPrefix = "TheApp/FooBar.app")) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("theApp")).isRegularFile()
      assertThat(installDir.resolve("ggg.txt")).doesNotExist()
    }

  @Test fun `test unpacking tar_gz cut dirs WSL`() {
    IoTestUtil.assumeUnix()

    testUnpacking(mockTarGZ.copy(packageRootPrefix = "TheApp/FooBar.app", os = "linux"), jdkInstaller = mockWSLInstaller) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("theApp")).isRegularFile()
      assertThat(installDir.resolve("ggg.txt")).doesNotExist()
    }
  }

  @Test fun `test unpacking tar_gz cut dirs 2`() {
    IoTestUtil.assumeUnix()

    testUnpacking(mockTarGZ2.copy(packageRootPrefix = "this/jdk")) {
      assertThat(installDir.resolve("bin/java")).isRegularFile()
      assertThat(installDir.resolve("bin/javac")).isRegularFile()
      assertThat(installDir.resolve("file")).isRegularFile()
      assertThat(installDir.resolve("bin/symlink")).isSymbolicLink().hasSameTextualContentAs(installDir.resolve("file"))
    }
  }

  @Test fun `test unpacking tar_gz cut dirs complex prefix`() =
    testUnpacking(mockTarGZ.copy(packageRootPrefix = "./TheApp/FooBar.app")) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("theApp")).isRegularFile()
      assertThat(installDir.resolve("ggg.txt")).doesNotExist()
    }

  @Test fun `test unpacking tar_gz cut dirs and prefix`() =
    testUnpacking(mockTarGZ.copy(packageRootPrefix = "TheApp/FooBar.app")) {
      assertThat(installDir.resolve("theApp")).isRegularFile()
      assertThat(installDir.resolve("ggg.txt")).doesNotExist()
    }

  @Test(expected = Exception::class)
  fun `test unpacking tar_gz invalid size`() = testUnpacking(mockTarGZ.copy(archiveSize = 234234))

  @Test(expected = Exception::class)
  fun `test unpacking tar_gz invalid checksum`() = testUnpacking(mockTarGZ.copy(sha256 = "234234"))

  @Test fun `test unpacking zip`() =
    testUnpacking(mockZip) {
      assertThat(installDir).isEqualTo(javaHome)
      assertThat(installDir.resolve("folder/readme2")).isDirectory()
      assertThat(installDir.resolve("folder/file")).isRegularFile()
    }

  @Test fun `test unpacking zip package path`() =
    testUnpacking(mockZip.copy(packageToBinJavaPrefix = "folder")) {
      assertThat(javaHome.resolve("readme2")).isDirectory()
      assertThat(javaHome.resolve("file")).isRegularFile()
      assertThat(installDir.resolve("folder/readme2")).isDirectory()
      assertThat(installDir.resolve("folder/file")).isRegularFile()
    }

  @Test fun `test unpacking zip package path WSL`() {
    IoTestUtil.assumeUnix()

    testUnpacking(mockZip.copy(packageToBinJavaPrefix = "folder", os = "linux"), mockWSLInstaller) {
      assertThat(javaHome.resolve("readme2")).isDirectory()
      assertThat(javaHome.resolve("file")).isRegularFile()
      assertThat(installDir.resolve("folder/readme2")).isDirectory()
      assertThat(installDir.resolve("folder/file")).isRegularFile()
    }
  }

  @Test(expected = Exception::class)
  fun `test unpacking zip invalid size`() = testUnpacking(mockZip.copy(archiveSize = 234))

  @Test(expected = Exception::class)
  fun `test unpacking zip invalid checksum`() = testUnpacking(mockZip.copy(sha256 = "234"))

  @Test fun `test unpacking zip cut dirs and wrong prefix`() =
    testUnpacking(mockZip.copy(packageRootPrefix = "wrong")) {
      assertThat(installDir.resolve("folder/readme2")).doesNotExist()
      assertThat(installDir.resolve("folder/file")).doesNotExist()
    }

  @Test fun `test unpacking zip cut dirs and prefix`() =
    testUnpacking(mockZip.copy(packageRootPrefix = "folder")) {
      assertThat(installDir.resolve("readme2")).isDirectory()
      assertThat(installDir.resolve("file")).isRegularFile()
    }

  @Test fun `test unpacking zip and prefix`() =
    testUnpacking(mockZip.copy(packageRootPrefix = "folder/file")) {
      assertThat(installDir.resolve("readme2")).doesNotExist()
      assertThat(installDir.resolve("folder/file")).doesNotExist()
    }

  @Test(expected = Exception::class)
  fun `test unpacking zip and prefix WSL`() {
    IoTestUtil.assumeWslPresence()

    testUnpacking(mockZip.copy(os = "linux", packageRootPrefix = "folder/file"), mockWSLInstaller) {
      assertThat(installDir.resolve("readme2")).doesNotExist()
      assertThat(installDir.resolve("folder/file")).doesNotExist()
    }
  }

  private fun testUnpacking(item: JdkItem,
                            jdkInstaller: JdkInstallerBase = JdkInstaller.getInstance(),
                            resultDir: JdkInstallRequest.() -> Unit = { error("must not reach here") }) {
    val dir = tempDir.newDirectoryPath()
    val task = jdkInstaller.prepareJdkInstallationDirect(item, dir)
    jdkInstaller.installJdk(task, null, null)

    assertThat(task.installDir).isDirectory().isEqualTo(dir)
    assertThat(task.javaHome).isDirectory()

    LOG.debug("Unpacked files:")
    dir.toFile().walkTopDown().forEach {
      LOG.debug("  <install dir>${it.path.removePrefix(dir.toString())}")
    }

    task.resultDir()
  }
}
