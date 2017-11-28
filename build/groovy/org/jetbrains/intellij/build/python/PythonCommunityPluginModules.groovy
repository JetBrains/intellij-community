/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  static PluginLayout pythonCommunityPluginLayout(@DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body = {}) {
    def pluginXmlModules = [
      "IntelliLang-python",
      "ipnb",
    ]
    pythonPlugin(PYTHON_COMMUNITY_PLUGIN_MODULE, "python-ce", "python-community-plugin-build-patches",
                 COMMUNITY_MODULES) {
      withProjectLibrary("markdown4j-2.2")  // Required for ipnb
      pluginXmlModules.each { module ->
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
        withModule(module, mainJarName, false)
      }
      withModule(buildPatchesModule, mainJarName, false)
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
