// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.PluginLayout

/**
 * @author nik
 */
class PythonCommunityPluginModules {
  static List<String> COMMUNITY_MODULES = [
    "intellij.python.langInjection",
    "intellij.python.ipnb",
    "intellij.python.community",
    "intellij.python.community.plugin",
    "intellij.python.community.plugin.java",
    "intellij.python.configure",
    "intellij.python.community.plugin.minor",
    "intellij.python.psi",
    "intellij.python.pydev",
    "intellij.python.community.impl",
  ]
  public static String PYTHON_COMMUNITY_PLUGIN_MODULE = "intellij.python.community.plugin.resources"
  static final List<String> PYCHARM_ONLY_PLUGIN_MODULES = [
    "intellij.python.langInjection",
    "intellij.python.ipnb",
  ]

  static PluginLayout pythonCommunityPluginLayout(@DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    pythonPlugin(PYTHON_COMMUNITY_PLUGIN_MODULE, "python-ce", "intellij.python.community.plugin.buildPatches",
                 COMMUNITY_MODULES) {
      withProjectLibrary("markdown4j")  // Required for ipnb
      PYCHARM_ONLY_PLUGIN_MODULES.each { module ->
        excludeFromModule(module, "META-INF/plugin.xml")
      }
      excludeFromModule(PYTHON_COMMUNITY_PLUGIN_MODULE, "META-INF/python-plugin-dependencies.xml")
      body.delegate = delegate
      body()
    }
  }

  static PluginLayout pythonPlugin(String mainModuleName, String name, String buildPatchesModule, List<String> modules,
                                   @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    PluginLayout.plugin(mainModuleName) {
      directoryName = name
      mainJarName = "${name}.jar"
      modules.each { module ->
        withModule(module, mainJarName, null)
      }
      withModule(buildPatchesModule, mainJarName, null)
      withResourceFromModule("intellij.python.helpers", "", "helpers")
      withCustomVersion { BuildContext context ->
        // TODO: Make the Python plugin follow the conventional scheme for plugin versioning, build the plugin together with the IDE
        def pluginBuildNumber = getPluginBuildNumber()
        "$context.applicationInfo.majorVersion.$context.applicationInfo.minorVersionMainPart.$pluginBuildNumber"
      }
      doNotCreateSeparateJarForLocalizableResources()
      body.delegate = delegate
      body()
    }
  }

  static String getPluginBuildNumber() {
    System.getProperty("build.number", "SNAPSHOT")
  }
}
