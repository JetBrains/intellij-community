// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ResourcesGenerator
import org.jetbrains.intellij.build.impl.PluginLayout

/**
 * @author nik
 */
class PythonCommunityPluginModules {
  static List<String> COMMUNITY_MODULES = [
    "intellij.python.community",
    "intellij.python.community.plugin",
    "intellij.python.community.plugin.java",
    "intellij.python.community.plugin.minor",
    "intellij.python.psi",
    "intellij.python.psi.impl",
    "intellij.python.pydev",
    "intellij.python.community.impl",
    "intellij.python.langInjection",
    "intellij.python.copyright",
    "intellij.python.terminal",
    "intellij.python.reStructuredText",
  ]
  static PluginLayout pythonCommunityPluginLayout(@DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    pythonPlugin("intellij.python.community.plugin", "python-ce", COMMUNITY_MODULES) {
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
        withModule(module, mainJarName, null)
      }
      withModule(mainModuleName, mainJarName)
      withGeneratedResources(new HelpersGenerator(), "helpers")
      doNotCreateSeparateJarForLocalizableResources()
      withProjectLibrary("libthrift")  // Required for "Python Console" in intellij.python.community.impl module
      body.delegate = delegate
      body()
    }
  }

  static String getPluginBuildNumber() {
    System.getProperty("build.number", "SNAPSHOT")
  }
}

class HelpersGenerator implements ResourcesGenerator {
  @Override
  File generateResources(BuildContext context) {
    String output = "$context.paths.temp/python/helpers"
    context.ant.copy(todir: output) {
      fileset(dir: "$context.paths.communityHome/python/helpers") {
        exclude(name: "**/setup.py")
        exclude(name: "pydev/pydev_test*")
        exclude(name: "tests/")
      }
    }
    return new File(output)
  }
}
