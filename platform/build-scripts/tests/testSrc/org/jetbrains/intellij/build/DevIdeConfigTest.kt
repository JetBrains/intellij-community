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

/**
 * The composer writes the config file, and `PreBuiltDevMain` and the IDE Starter runner read it. The fixtures here
 * state the format that the composer writes, and the reader must accept exactly that.
 */
class DevIdeConfigTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun aRelativeHomeIsResolvedAgainstTheConfigDir() {
    val configFile = tempDir.resolve("dist.ide.config")
    val home = tempDir.resolve("dist").createDirectories()
    // relative, so that the pair keeps naming each other after being read from a different path than it was written to
    Files.writeString(configFile, composedConfig(homePath = "dist", additionalModules = "intellij.devkit"))

    assertThat(DevIdeConfig.read(configFile).homePath()).isEqualTo(home)
  }

  @Test
  fun anAbsoluteHomeIsTakenVerbatim() {
    val configFile = tempDir.resolve("config").createDirectories().resolve("dist.ide.config")
    val home = tempDir.resolve("elsewhere/dist").createDirectories()
    Files.writeString(configFile, composedConfig(homePath = home.toString().replace('\\', '/'), additionalModules = ""))

    assertThat(DevIdeConfig.read(configFile).homePath()).isEqualTo(home)
  }

  @Test
  fun theDistributionDescribesWhatItWasAssembledAs() {
    val configFile = tempDir.resolve("dist.ide.config")
    tempDir.resolve("dist").createDirectories()
    Files.writeString(
      configFile,
      composedConfig(homePath = "dist", platformPrefix = "GoLand", additionalModules = "intellij.devkit,intellij.air.plugin"),
    )

    val content = DevIdeConfig.read(configFile)
    assertThat(content.mainClassName()).isEqualTo("com.intellij.idea.Main")
    assertThat(content.platformPrefix()).isEqualTo("GoLand")
    // order is the writer's, so a consumer comparing sets and a human reading the file see the same thing
    assertThat(content.additionalModules()).containsExactly("intellij.devkit", "intellij.air.plugin")
  }

  @Test
  fun noAdditionalModulesReadsAsAnEmptyList() {
    val configFile = tempDir.resolve("dist.ide.config")
    tempDir.resolve("dist").createDirectories()
    Files.writeString(configFile, composedConfig(homePath = "dist", additionalModules = ""))

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

  /** A config file as `write_dev_ide_config` of the composer writes it: four keys, each on its own line. */
  private fun composedConfig(homePath: String, platformPrefix: String = "idea", additionalModules: String): String {
    return "home.path=$homePath\n" +
           "main.class.name=com.intellij.idea.Main\n" +
           "platform.prefix=$platformPrefix\n" +
           "additional.modules=$additionalModules\n"
  }
}
