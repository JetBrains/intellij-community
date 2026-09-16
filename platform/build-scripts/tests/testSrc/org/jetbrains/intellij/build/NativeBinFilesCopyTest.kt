// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.testFramework.common.timeoutRunBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetMapping
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetTransform
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.copyDeclaredOsSpecificFiles
import org.jetbrains.intellij.build.impl.copyDistFiles
import org.jetbrains.intellij.build.impl.copyNativeBinDir
import org.jetbrains.intellij.build.impl.copyNativeBinFileToDir
import org.jetbrains.intellij.build.impl.registerPlatformDistFiles
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class NativeBinFilesCopyTest {
  @Test
  @Timeout(30)
  fun `a product without dist files registers none`() {
    timeoutRunBlocking {
      val context = mock(BuildContext::class.java)

      registerPlatformDistFiles(PlatformLayout(), context)
      TestProductProperties().registerDistFiles(context)

      verifyNoInteractions(context)
    }
  }

  @Test
  @Timeout(30)
  fun `declared platform dist files are registered before the product files`(@TempDir tempDir: Path) {
    timeoutRunBlocking {
      val source = tempDir.resolve("binary-test").also { it.writeText("binary bytes") }
      val binaryFile = DistFile(LocalDistFileContent(source, isExecutable = true), "lib/test/binary-test")
      val configFile = DistFile(InMemoryDistFileContent("config bytes".encodeToByteArray()), "bin/test.properties")
      val context = mock(BuildContext::class.java)
      val properties = object : TestProductProperties() {
        override fun registerDistFiles(context: BuildContext) {
          context.addDistFile(configFile)
        }
      }
      val platformLayout = PlatformLayout()
      platformLayout.withDistFiles(OsFamily.LINUX, JvmArchitecture.x64, testDistFileSpec("binary-test")) { listOf(binaryFile) }

      registerPlatformDistFiles(platformLayout, context)
      properties.registerDistFiles(context)

      val registrationOrder = inOrder(context)
      registrationOrder.verify(context).addDistFile(binaryFile)
      registrationOrder.verify(context).addDistFile(configFile)
      verifyNoMoreInteractions(context)
      `when`(context.getDistFiles(OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC)).thenReturn(listOf(binaryFile, configFile))
      val runDir = tempDir.resolve("run")

      copyDistFiles(runDir, OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC, context)

      val binary = runDir.resolve("lib/test/binary-test")
      assertThat(binary).hasContent("binary bytes")
      assertThat(runDir.resolve("bin/test.properties")).hasContent("config bytes")
      if (Files.getFileStore(binary).supportsFileAttributeView("posix")) {
        assertThat(Files.isExecutable(binary)).isTrue()
      }

      source.writeText("updated binary bytes")
      copyDistFiles(runDir, OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC, context)

      assertThat(binary).hasContent("updated binary bytes")
    }
  }

  @Test
  fun `declared os-specific files are copied for their platform only and reported`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("download/restarter").also {
      it.parent.createDirectories()
      it.writeText("restarter bytes")
    }
    val distributionDir = tempDir.resolve("dist").createDirectories()
    val context = mock(BuildContext::class.java)
    val platformLayout = PlatformLayout()
    platformLayout.withOsSpecificFiles(OsFamily.LINUX, JvmArchitecture.x64, testDistFileSpec("restarter")) { dir, _ ->
      listOf(copyNativeBinFileToDir(source, dir.resolve("bin").createDirectories()))
    }

    val copied = copyDeclaredOsSpecificFiles(platformLayout, distributionDir, OsFamily.LINUX, JvmArchitecture.x64, context)
    val otherPlatform = copyDeclaredOsSpecificFiles(platformLayout, distributionDir, OsFamily.MACOS, JvmArchitecture.x64, context)

    assertThat(copied).containsExactly(distributionDir.resolve("bin/restarter"))
    assertThat(distributionDir.resolve("bin/restarter")).hasContent("restarter bytes")
    assertThat(otherPlatform).isEmpty()
    verifyNoInteractions(context)
  }

  @Test
  fun `a repeated native bin layout replaces files and reports current outputs`(@TempDir tempDir: Path) {
    val directSource = tempDir.resolve("download/restarter").also {
      it.parent.createDirectories()
      it.writeText("first restarter")
    }
    val treeSource = tempDir.resolve("checkout/bin").createDirectories()
    val treeFile = treeSource.resolve("fsnotifier").also { it.writeText("first watcher") }
    treeSource.resolve("excluded").writeText("excluded")
    val binDir = tempDir.resolve("dist/bin").createDirectories()

    copyNativeBinFileToDir(directSource, binDir)
    copyNativeBinDir(treeSource, binDir, fileFilter = { it.fileName.toString() != "excluded" })

    directSource.writeText("second restarter")
    treeFile.writeText("second watcher")
    val directTarget = copyNativeBinFileToDir(directSource, binDir)
    val treeTargets = copyNativeBinDir(treeSource, binDir, fileFilter = { it.fileName.toString() != "excluded" })

    assertThat(directTarget).isEqualTo(binDir.resolve("restarter"))
    assertThat(directTarget).hasContent("second restarter")
    assertThat(treeTargets).containsExactly(binDir.resolve("fsnotifier"))
    assertThat(binDir.resolve("fsnotifier")).hasContent("second watcher")
    assertThat(binDir.resolve("excluded")).doesNotExist()
  }
}

/** One executable named [fileName] out of a test archive, at `lib/test/<fileName>`: the smallest valid platform dist file declaration. */
internal fun testDistFileSpec(fileName: String): DevPluginLayoutAssetSpec {
  return DevPluginLayoutAssetSpec(
    sources = listOf(DevPluginLayoutAssetSource.BazelTarget(label = "@dev_launch_test//:files", kind = "archive", fileName = "test.tar.gz")),
    assets = listOf(DevPluginLayoutAsset(
      destination = "lib/test",
      sources = listOf(0),
      transform = DevPluginLayoutAssetTransform.archiveTree(mappings = listOf(DevPluginLayoutAssetMapping(pattern = fileName))),
      mode = 493,
    )),
  )
}

private open class TestProductProperties : ProductProperties() {
  override val baseFileName: String = "test"

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = baseFileName

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer? = null

  override fun createLinuxCustomizer(projectHome: Path): LinuxDistributionCustomizer? = null

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer? = null

  override fun getProductContentDescriptor(): ProductModulesContentSpec? = null
}
