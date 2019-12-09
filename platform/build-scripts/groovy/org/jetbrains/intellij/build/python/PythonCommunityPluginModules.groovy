// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.impl.PluginLayout

/**
 * @author nik
 */
class PythonCommunityPluginModules {
  static final List<String> PYCHARM_ONLY_PLUGIN_MODULES = [
    "intellij.python.langInjection",
    "intellij.python.copyright",
    "intellij.python.terminal",
    "intellij.python.reStructuredText",
  ]
  static List<String> COMMUNITY_MODULES = ["intellij.python.community",
                                           "intellij.python.community.plugin",
                                           "intellij.python.community.plugin.java",
                                           "intellij.python.configure",
                                           "intellij.python.community.plugin.minor",
                                           "intellij.python.psi",
                                           "intellij.python.psi.impl",
                                           "intellij.python.pydev",
                                           "intellij.python.community.impl",
                                          ] + PYCHARM_ONLY_PLUGIN_MODULES
  public static String PYTHON_COMMUNITY_PLUGIN_MODULE = "intellij.python.community.plugin.resources"

  static PluginLayout pythonCommunityPluginLayout(@DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    pythonPlugin(PYTHON_COMMUNITY_PLUGIN_MODULE, "python-ce", COMMUNITY_MODULES) {
      withProjectLibrary("markdown4j")  // Required for ipnb
      PYCHARM_ONLY_PLUGIN_MODULES.each { module ->
        excludeFromModule(module, "META-INF/plugin.xml")
      }
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
      withResourceFromModule("intellij.python.helpers", "", "helpers")
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
