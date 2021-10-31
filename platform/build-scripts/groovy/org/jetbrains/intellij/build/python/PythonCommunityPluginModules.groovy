// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.python

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.BuildHelper
import org.jetbrains.intellij.build.impl.PluginLayout

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer
import java.util.function.Predicate

@CompileStatic
final class PythonCommunityPluginModules {
  static final List<String> COMMUNITY_MODULES = List.of(
    "intellij.python.community",
    "intellij.python.community.plugin.impl",
    "intellij.python.community.plugin.java",
    "intellij.python.psi",
    "intellij.python.psi.impl",
    "intellij.python.pydev",
    "intellij.python.community.impl",
    "intellij.python.langInjection",
    "intellij.python.copyright",
    "intellij.python.terminal",
    "intellij.python.grazie",
    "intellij.python.markdown",
    "intellij.python.reStructuredText",
    "intellij.python.sdk",
    "intellij.python.featuresTrainer",
    "intellij.jupyter.core"
  )
  static final String pythonCommunityName = "python-ce"

  static PluginLayout pythonCommunityPluginLayout(@DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    def communityOnlyModules = [
      "intellij.python.community.plugin",
      "intellij.python.community.plugin.minor",
    ]
    pythonPlugin("intellij.python.community.plugin", pythonCommunityName, COMMUNITY_MODULES + communityOnlyModules) {
      body.delegate = delegate
      body()
    }
  }

  static PluginLayout pythonPlugin(String mainModuleName, String name, List<String> modules,
                                   @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    PluginLayout.plugin(mainModuleName) {
      directoryName = name
      mainJarName = "${name}.jar"
      modules.each { module ->
        withModule(module, mainJarName)
      }
      withModule(mainModuleName, mainJarName)
      withGeneratedResources(new HelpersGenerator())
      withProjectLibrary("libthrift")  // Required for "Python Console" in intellij.python.community.impl module
      body.delegate = delegate
      body()
    }
  }

  static String getPluginBuildNumber() {
    System.getProperty("build.number", "SNAPSHOT")
  }
}

@CompileStatic
final class HelpersGenerator implements BiConsumer<Path, BuildContext> {
  @Override
  void accept(Path targetDir, BuildContext context) {
    Path output = targetDir.resolve("helpers")
    Files.createDirectories(output)
    BuildHelper.getInstance(context).copyDir(context.paths.communityHomeDir.resolve("python/helpers"), output, new Predicate<Path>() {
      @Override
      boolean test(Path path) {
        if (path.endsWith("tests") || path.endsWith(".idea")) {
          return false
        }
        if (path.parent?.fileName?.toString() == "pydev") {
          return !path.fileName.toString().startsWith("pydev_test")
        }
        return true
      }
    }, new Predicate<Path>() {
      @Override
      boolean test(Path path) {
        return !path.endsWith("setup.py")
      }
    })
  }
}
