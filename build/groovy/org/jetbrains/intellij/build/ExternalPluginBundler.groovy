// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class ExternalPluginBundler {
  static void bundle(String pluginName,
                     String dependenciesPath,
                     BuildContext context,
                     String targetDirectory,
                     String buildTaskName = pluginName) {
    Path dependenciesProjectDir = Path.of(dependenciesPath)
    new GradleRunner(dependenciesProjectDir, context.options, context.paths.buildDependenciesCommunityRoot, Collections.<String>emptyList()).run(
      "Downloading $pluginName plugin...", "setup${buildTaskName}Plugin")
    Properties properties = new Properties()
    Files.newInputStream(dependenciesProjectDir.resolve("gradle.properties")).withCloseable {
      properties.load(it)
    }

    Path pluginZip = dependenciesProjectDir.resolve("build/$pluginName/$pluginName-${properties.getProperty("${buildTaskName}PluginVersion")}.zip")
    if (!Files.exists(pluginZip)) {
      throw new IllegalStateException("$pluginName bundled plugin is not found. Plugin path:${pluginZip}")
    }
    extractPlugin(pluginZip, targetDirectory)
  }

  static void extractPlugin(Path pluginZip, String targetDirectory) {
    new Decompressor.Zip(pluginZip).extract(Path.of(targetDirectory, "plugins"))
  }
}