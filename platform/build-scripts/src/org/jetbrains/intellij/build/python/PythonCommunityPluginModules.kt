// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.impl.PluginLayout

object PythonCommunityPluginModules {

  const val pythonCommunityName: String = "python-ce"

  fun pythonCommunityPluginLayout(body: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return pythonPlugin("intellij.python.community.plugin", pythonCommunityName, emptyList()) { spec ->
      body?.invoke(spec)
    }
  }

  fun pythonPlugin(mainModuleName: String, name: String, modules: List<String>, body: (PluginLayout.PluginLayoutSpec) -> Unit): PluginLayout {
    return PluginLayout.pluginAutoWithCustomDirName(mainModuleName, name) { spec ->
      spec.withModules(modules)
      if (mainModuleName == "intellij.python.community.plugin") {
        spec.withResourceTree(
          moduleName = "intellij.python.helpers",
          resourcePath = "",
          relativeOutputPath = "helpers",
          excludes = listOf("{setup.py,conftest.py}", "**/{setup.py,conftest.py}"),
          directoryExcludes = listOf("{tests,.idea}", "**/{tests,.idea}", "pydev/pydev_test*", "**/pydev/pydev_test*"),
        )
      }

      // required for "Python Console" in PythonCore plugin
      @Suppress("SpellCheckingInspection")
      body(spec)
    }
  }

  fun getPluginBuildNumber(): String = System.getProperty("build.number", "SNAPSHOT")
}
