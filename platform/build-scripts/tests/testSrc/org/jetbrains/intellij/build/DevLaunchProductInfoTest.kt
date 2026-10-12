// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.buildScripts.devLaunch.readCustomCommand
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

  private fun writeProductInfo(relativePath: String, mainClass: String) {
    home.resolve(relativePath).createParentDirectories().writeText(
      """
      {"name": "IDEA", "version": "1", "buildNumber": "1", "productCode": "IU",
       "launch": [{"os": "macOS", "launcherPath": "bin/idea", "vmOptionsFilePath": "bin/idea.vmoptions",
                   "customCommands": [{"commands": ["run"], "mainClass": "$mainClass"}]}]}
      """.trimIndent()
    )
  }
}
