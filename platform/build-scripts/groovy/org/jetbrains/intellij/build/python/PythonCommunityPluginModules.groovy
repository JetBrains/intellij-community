/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.PluginLayout

/**
 * @author nik
 */
class PythonCommunityPluginModules {
  static List<String> COMMUNITY_MODULES = [
    "IntelliLang-python",
    "ipnb",
    "python-openapi",
    "python-community-plugin-core",
    "python-community-plugin-java",
    "python-community-configure",
    "python-community-plugin-minor",
    "python-psi-api",
    "python-pydev",
    "python-community",
  ]
  public static String PYTHON_COMMUNITY_PLUGIN_MODULE = "python-community-plugin-resources"
  static final List<String> PYCHARM_ONLY_PLUGIN_MODULES = [
    "IntelliLang-python",
    "ipnb",
  ]

  static PluginLayout pythonCommunityPluginLayout(@DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    pythonPlugin(PYTHON_COMMUNITY_PLUGIN_MODULE, "python-ce", "python-community-plugin-build-patches",
                 COMMUNITY_MODULES) {
      withProjectLibrary("markdown4j-2.2")  // Required for ipnb
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
      withResourceFromModule("python-helpers", "", "helpers")
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
