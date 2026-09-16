// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.testFramework.common.timeoutRunBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.copyDistFiles
import org.jetbrains.intellij.build.impl.copyNativeBinDir
import org.jetbrains.intellij.build.impl.copyNativeBinFileToDir
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
  fun `a product without an IJent registrar registers no files`() {
    timeoutRunBlocking {
      val context = mock(BuildContext::class.java)

      TestProductProperties().registerDistFiles(context)

      verifyNoInteractions(context)
    }
  }

  @Test
  @Timeout(30)
  fun `registered files include IJent executables`(@TempDir tempDir: Path) {
    timeoutRunBlocking {
      val source = tempDir.resolve("ijent").also { it.writeText("ijent bytes") }
      val binaryFile = DistFile(LocalDistFileContent(source, isExecutable = true), "lib/ijent/ijent-test")
      val configFile = DistFile(InMemoryDistFileContent("config bytes".encodeToByteArray()), "bin/test.properties")
      val context = mock(BuildContext::class.java)
      val properties = object : TestProductProperties() {
        override fun registerDistFiles(context: BuildContext) {
          super.registerDistFiles(context)
          context.addDistFile(configFile)
        }
      }
      properties.ijentDistributionRegistrar = { buildContext ->
        buildContext.addDistFile(binaryFile)
      }

      properties.registerDistFiles(context)

      val registrationOrder = inOrder(context)
      registrationOrder.verify(context).addDistFile(binaryFile)
      registrationOrder.verify(context).addDistFile(configFile)
      verifyNoMoreInteractions(context)
      `when`(context.getDistFiles(OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC)).thenReturn(listOf(binaryFile, configFile))
      val runDir = tempDir.resolve("run")

      copyDistFiles(runDir, OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC, context)

      val binary = runDir.resolve("lib/ijent/ijent-test")
      assertThat(binary).hasContent("ijent bytes")
      assertThat(runDir.resolve("bin/test.properties")).hasContent("config bytes")
      if (Files.getFileStore(binary).supportsFileAttributeView("posix")) {
        assertThat(Files.isExecutable(binary)).isTrue()
      }

      source.writeText("updated ijent bytes")
      copyDistFiles(runDir, OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC, context)

      assertThat(binary).hasContent("updated ijent bytes")
    }
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

private open class TestProductProperties : ProductProperties() {
  override val baseFileName: String = "test"

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = baseFileName

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer? = null

  override fun createLinuxCustomizer(projectHome: Path): LinuxDistributionCustomizer? = null

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer? = null

  override fun getProductContentDescriptor(): ProductModulesContentSpec? = null
}
