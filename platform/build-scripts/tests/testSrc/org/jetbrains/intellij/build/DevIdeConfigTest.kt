// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.devIdeConfig.DevIdeConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/**
 * The config file is written by `DevDistMain` and read by `PreBuiltDevMain` and by the IDE Starter runner, in three
 * different processes, so the round trip is the contract - not either half of it.
 */
class DevIdeConfigTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun aHomeUnderTheConfigDirIsNamedRelatively() {
    val configFile = tempDir.resolve("dist.ide.config")
    val home = tempDir.resolve("dist").createDirectories()

    DevIdeConfig.write(configFile, home, "com.intellij.idea.Main", "idea", listOf("intellij.devkit"))

    // relative, so that the pair keeps naming each other after being read from a different path than it was written to
    assertThat(configFile.readText()).contains("home.path=dist\n")
    assertThat(DevIdeConfig.read(configFile).homePath()).isEqualTo(home)
  }

  @Test
  fun aHomeOutsideTheConfigDirStaysAbsolute() {
    val configDir = tempDir.resolve("config").createDirectories()
    val configFile = configDir.resolve("dist.ide.config")
    val home = tempDir.resolve("elsewhere/dist").createDirectories()

    DevIdeConfig.write(configFile, home, "com.intellij.idea.Main", "idea", emptyList())

    assertThat(configFile.readText()).contains("home.path=${home.toString().replace('\\', '/')}\n")
    assertThat(DevIdeConfig.read(configFile).homePath()).isEqualTo(home)
  }

  @Test
  fun theDistributionDescribesWhatItWasAssembledAs() {
    val configFile = tempDir.resolve("dist.ide.config")

    DevIdeConfig.write(
      configFile,
      tempDir.resolve("dist").createDirectories(),
      "com.intellij.idea.Main",
      "GoLand",
      listOf("intellij.devkit", "intellij.air.plugin"),
    )

    val content = DevIdeConfig.read(configFile)
    assertThat(content.mainClassName()).isEqualTo("com.intellij.idea.Main")
    assertThat(content.platformPrefix()).isEqualTo("GoLand")
    // order is the caller's, so a consumer comparing sets and a human reading the file see the same thing
    assertThat(content.additionalModules()).containsExactly("intellij.devkit", "intellij.air.plugin")
  }

  @Test
  fun noAdditionalModulesReadsAsAnEmptyList() {
    val configFile = tempDir.resolve("dist.ide.config")

    DevIdeConfig.write(configFile, tempDir.resolve("dist").createDirectories(), "com.intellij.idea.Main", "idea", emptyList())

    assertThat(DevIdeConfig.read(configFile).additionalModules()).isEmpty()
  }

  @Test
  fun aConfigFileWrittenByHandNeedsOnlyTheHome() {
    // the containerized case: `BuildIdeForDocker` writes these two keys and nothing else
    val configFile = tempDir.resolve("dist.ide.config")
    Files.writeString(configFile, "home.path=dist\nmain.class.name=com.intellij.idea.Main\n")
    tempDir.resolve("dist").createDirectories()

    val content = DevIdeConfig.read(configFile)
    assertThat(content.homePath()).isEqualTo(tempDir.resolve("dist"))
    // absent is not "anything goes": a consumer asking for plugin modules must fail against this, not assume
    assertThat(content.platformPrefix()).isNull()
    assertThat(content.additionalModules()).isEmpty()
  }

  @Test
  fun aConfigFileWithoutAHomeFails() {
    val configFile = tempDir.resolve("dist.ide.config")
    Files.writeString(configFile, "main.class.name=com.intellij.idea.Main\n")

    assertThatThrownBy { DevIdeConfig.read(configFile) }
      .hasMessageContaining("home.path")
      .hasMessageContaining(configFile.toString())
  }

  @Test
  fun anExistingPathIsTakenVerbatim() {
    val configFile = tempDir.resolve("dist.ide.config")
    Files.writeString(configFile, "home.path=dist\n")

    assertThat(DevIdeConfig.resolveConfigFile(configFile.toString())).isEqualTo(configFile)
  }

  @Test
  fun aPathThatNamesNothingReportsWhereItLooked() {
    assertThatThrownBy { DevIdeConfig.resolveConfigFile("build/idea_air_dist.ide.config") }
      .hasMessageContaining("names neither an existing file nor a runfile")
      .hasMessageContaining("RUNFILES_MANIFEST_FILE")
  }
}
