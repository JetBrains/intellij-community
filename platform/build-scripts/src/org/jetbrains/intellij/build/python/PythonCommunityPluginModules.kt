// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.impl.PluginLayout

object PythonCommunityPluginModules {

  const val pythonCommunityName: String = "python-ce"

  /**
   * The pydev tree of the Python helpers. The path is relative to the content root of the module. The debugger egg of a
   * Python plugin reads the whole tree, so the community Python layout declares it as a dev-distribution source tree.
   */
  @JvmField
  val pydevSourceTree: DevPluginLayoutAssetSource.ModuleDirectory = DevPluginLayoutAssetSource.ModuleDirectory(
    moduleName = "intellij.python.helpers",
    path = "pydev",
  )

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
          excludedFiles = listOf("setup.py", "conftest.py"),
          excludedDirectories = listOf("tests", ".idea", "pydev/pydev_test*"),
        )
        spec.withDevDistSourceTree(pydevSourceTree)
      }

      // required for "Python Console" in PythonCore plugin
      @Suppress("SpellCheckingInspection")
      body(spec)
    }
  }

  fun getPluginBuildNumber(): String = System.getProperty("build.number", "SNAPSHOT")
}
