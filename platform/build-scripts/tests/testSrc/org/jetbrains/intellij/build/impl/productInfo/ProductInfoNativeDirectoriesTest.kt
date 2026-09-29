// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import com.intellij.platform.buildData.productInfo.ProductInfoLaunchData
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ProductInfoNativeDirectoriesTest {
  @Test
  fun `a Linux launch names its native directories relative to the installation root`() {
    val launch = launch(
      os = "Linux",
      arguments = listOf(
        $$"-Xbootclasspath/a:$IDE_HOME/lib/nio-fs.jar",
        $$"-Djna.boot.library.path=$IDE_HOME/plugins/jna-plugin/lib/jna/amd64",
        "-Djna.nosys=true",
        $$"-Dpty4j.preferred.native.folder=$IDE_HOME/plugins/pty4j-plugin/lib/pty4j",
        $$"-Dskiko.library.path=$IDE_HOME/plugins/skiko-plugin/lib/skiko-awt-runtime-all",
      ),
    )

    assertThat(nativeDirectoriesOfLaunch(launch)).containsExactly(
      "plugins/jna-plugin/lib/jna/amd64",
      "plugins/pty4j-plugin/lib/pty4j",
      "plugins/skiko-plugin/lib/skiko-awt-runtime-all",
    )
  }

  @Test
  fun `a macOS launch resolves the app package from the Resources directory`() {
    val launch = launch(os = "macOS", arguments = listOf($$"-Djna.boot.library.path=$APP_PACKAGE/Contents/plugins/jna-plugin/lib/jna/aarch64"))

    assertThat(nativeDirectoriesOfLaunch(launch)).containsExactly("../plugins/jna-plugin/lib/jna/aarch64")
  }

  @Test
  fun `a Windows launch and a custom command share one directory once`() {
    val directory = "%IDE_HOME%/plugins/pty4j-plugin/lib/pty4j"
    val launch = launch(
      os = "Windows",
      arguments = listOf("-Dpty4j.preferred.native.folder=$directory"),
      customCommands = listOf(CustomCommandLaunchData(commands = listOf("thinClient"), additionalJvmArguments = listOf("-Dpty4j.preferred.native.folder=$directory"))),
    )

    assertThat(nativeDirectoriesOfLaunch(launch)).containsExactly("plugins/pty4j-plugin/lib/pty4j")
  }

  @Test
  fun `a directory without a home macro is refused`() {
    val launch = launch(os = "Linux", arguments = listOf("-Djna.boot.library.path=/opt/idea/lib/jna"))

    assertThatThrownBy { nativeDirectoriesOfLaunch(launch) }.hasMessageContaining("no home macro")
  }

  private fun launch(os: String, arguments: List<String>, customCommands: List<CustomCommandLaunchData> = emptyList()): ProductInfoLaunchData {
    return ProductInfoLaunchData.create(
      os = os,
      arch = "amd64",
      launcherPath = "bin/idea",
      javaExecutablePath = "jbr/bin/java",
      vmOptionsFilePath = "bin/idea.vmoptions",
      bootClassPathJarNames = emptyList(),
      additionalJvmArguments = arguments,
      mainClass = "com.intellij.idea.Main",
      customCommands = customCommands,
    )
  }
}
