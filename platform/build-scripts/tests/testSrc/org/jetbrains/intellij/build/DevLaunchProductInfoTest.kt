// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.buildScripts.devLaunch.readCustomCommand
import com.intellij.platform.buildScripts.devLaunch.readVmOptions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

/** `DevMainImpl`, `PreBuiltDevMain` and the IDE Starter read `product-info.json` of a dev distribution through `DevLaunchProperties.kt`. */
class DevLaunchProductInfoTest {
  @TempDir
  lateinit var home: Path

  private val productionFile: String
    get() = if (SystemInfoRt.isMac) "Resources/product-info.json" else "product-info.json"

  /** The main vmoptions file, as the production writer names it relative to [productionFile]. */
  private val vmOptionsFilePath: String
    get() = if (SystemInfoRt.isMac) "../bin/idea.vmoptions" else "bin/idea64.vmoptions"

  @Test
  fun `the production place wins over the bin directory`() {
    writeProductInfo(productionFile, mainClass = "Production")
    writeProductInfo("bin/product-info.json", mainClass = "DevBuild")

    assertThat(readCustomCommand(home, "run")?.mainClass).isEqualTo("Production")
  }

  @Test
  fun `the bin directory of a dev build is the fallback`() {
    writeProductInfo("bin/product-info.json", mainClass = "DevBuild")

    assertThat(readCustomCommand(home, "run")?.mainClass).isEqualTo("DevBuild")
  }

  @Test
  fun `a home without the file fails and names the production place`() {
    assertThatThrownBy { readCustomCommand(home, "run") }
      .isInstanceOf(NoSuchFileException::class.java)
      .hasMessageContaining(home.resolve(productionFile).toString())
  }

  @Test
  fun `the main vmoptions file is the one of the first launch`() {
    writeProductInfo(productionFile, mainClass = "Production")
    val mainFile = writeVmOptionsFiles()

    assertThat(readVmOptions(home)).containsExactly("-Xmx1g", "-Djb.vmOptionsFile=$mainFile")
  }

  @Test
  fun `the bin directory of a dev build has the same base as the production place`() {
    writeProductInfo("bin/product-info.json", mainClass = "DevBuild")
    val mainFile = writeVmOptionsFiles()

    assertThat(readVmOptions(home)).containsExactly("-Xmx1g", "-Djb.vmOptionsFile=$mainFile")
  }

  @Test
  fun `a distribution of the other layout is read at its own production place`() {
    val (otherFile, otherVmOptionsPath) = if (SystemInfoRt.isMac) "product-info.json" to "bin/idea64.vmoptions" else "Resources/product-info.json" to "../bin/idea.vmoptions"
    writeProductInfo(otherFile, mainClass = "Cross", vmOptionsFilePath = otherVmOptionsPath)
    val mainFile = home.resolve(if (SystemInfoRt.isMac) "bin/idea64.vmoptions" else "bin/idea.vmoptions").createParentDirectories()
    mainFile.writeText("-Xmx1g\n")

    assertThat(readCustomCommand(home, "run")?.mainClass).isEqualTo("Cross")
    assertThat(readVmOptions(home)).containsExactly("-Xmx1g", "-Djb.vmOptionsFile=$mainFile")
  }

  @Test
  fun `a vmoptions file outside the home fails`() {
    writeProductInfo(productionFile, mainClass = "Production", vmOptionsFilePath = "../../../idea.vmoptions")

    assertThatThrownBy { readVmOptions(home) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("The vmOptionsFilePath '../../../idea.vmoptions' of ${home.resolve(productionFile)} is not a file of $home")
  }

  /** Writes the main vmoptions file and the one of an embedded frontend into `bin`, and returns the main file. */
  private fun writeVmOptionsFiles(): Path {
    val frontendFile = if (SystemInfoRt.isMac) "bin/jetbrains_client.vmoptions" else "bin/jetbrains_client64.vmoptions"
    home.resolve(frontendFile).createParentDirectories().writeText("-Xmx2g\n")
    val mainFile = home.resolve(if (SystemInfoRt.isMac) "bin/idea.vmoptions" else "bin/idea64.vmoptions")
    mainFile.writeText("-Xmx1g\n")
    return mainFile
  }

  private fun writeProductInfo(relativePath: String, mainClass: String, vmOptionsFilePath: String = this.vmOptionsFilePath) {
    home.resolve(relativePath).createParentDirectories().writeText(
      """
      {"name": "IDEA", "version": "1", "buildNumber": "1", "productCode": "IU",
       "launch": [{"os": "macOS", "launcherPath": "bin/idea", "vmOptionsFilePath": "$vmOptionsFilePath",
                   "customCommands": [{"commands": ["run"], "mainClass": "$mainClass"}]}]}
      """.trimIndent()
    )
  }
}
