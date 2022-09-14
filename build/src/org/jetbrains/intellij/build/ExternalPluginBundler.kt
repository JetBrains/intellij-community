// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.io.Decompressor
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object ExternalPluginBundler {
  @JvmStatic
  @JvmOverloads
  fun bundle(pluginName: String,
             dependenciesPath: String,
             context: BuildContext,
             targetDirectory: String,
             buildTaskName: String = pluginName) {
    val dependenciesProjectDir = Path.of(dependenciesPath)
    GradleRunner(gradleProjectDir = dependenciesProjectDir,
                 options = context.options,
                 communityRoot = context.paths.communityHomeDir,
                 additionalParams = emptyList())
      .run("Downloading $pluginName plugin...", "setup${buildTaskName}Plugin")
    val properties = Properties()
    Files.newInputStream(dependenciesProjectDir.resolve("gradle.properties")).use {
      properties.load(it)
    }

    val pluginZip = dependenciesProjectDir.resolve(
      "build/$pluginName/$pluginName-${properties.getProperty("${buildTaskName}PluginVersion")}.zip")
    check(Files.exists(pluginZip)) {
      "$pluginName bundled plugin is not found. Plugin path:${pluginZip}"
    }
    extractPlugin(pluginZip, targetDirectory)
  }

  @JvmStatic
  fun extractPlugin(pluginZip: Path, targetDirectory: String) {
    Decompressor.Zip(pluginZip).extract(Path.of(targetDirectory, "plugins"))
  }
}