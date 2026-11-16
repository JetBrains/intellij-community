// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.dev.LayoutAssetSource
import org.jetbrains.intellij.build.impl.PluginLayout

object PythonCommunityPluginModules {

  const val pythonCommunityName: String = "python-ce"

  /** The directory of the plugin that holds the whole `intellij.python.helpers` tree. */
  const val HELPERS_PLUGIN_DIR: String = "helpers"

  /**
   * The pydev tree of the Python helpers. The path is relative to the content root of the module. The debugger egg of a
   * Python plugin reads the whole tree, so the community Python layout declares it as a distribution source tree.
   */
  @JvmField
  val pydevSourceTree: LayoutAssetSource.ModuleDirectory = LayoutAssetSource.ModuleDirectory(
    moduleName = "intellij.python.helpers",
    path = "pydev",
  )

  const val MAIN_MODULE_NAME: String = "intellij.python.community.plugin"

  /** The main module of `com.intellij.python.basic`, which holds the Python parser, the AST-only PSI, and the lexer-based highlighting. */
  const val BASIC_PLUGIN_MAIN_MODULE_NAME: String = "intellij.python.basic.plugin"

  /**
   * The main modules of `PythonCore` and `com.intellij.python.basic`. A product that ignores the Python community plugin
   * as a compatible plugin ignores all of them.
   */
  @JvmField
  val ALL_MAIN_MODULE_NAMES: PersistentList<String> = persistentListOf(MAIN_MODULE_NAME, BASIC_PLUGIN_MAIN_MODULE_NAME)

  private fun pythonCommunityPlugin(body: ((PluginLayout.PluginLayoutSpec) -> Unit)?): PluginLayout {
    return pythonPlugin(MAIN_MODULE_NAME, pythonCommunityName, emptyList()) { spec ->
      body?.invoke(spec)
    }
  }

  /**
   * The layout of `PythonCore` and the layout of `com.intellij.python.basic`, in this order. [body] goes to the
   * `PythonCore` layout.
   */
  fun pythonCommunityPluginLayouts(body: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PersistentList<PluginLayout> {
    return persistentListOf(pythonCommunityPlugin(body), pythonBasicPlugin())
  }

  /**
   * The Marketplace variant of the `PythonCore` layout, without the assets that [pythonCommunityPluginLayouts] gets from
   * its caller. Register it only on top of [pythonCommunityPluginLayouts], which holds the layout of `com.intellij.python.basic`.
   */
  fun pythonCommunityMarketplacePluginLayout(): PluginLayout {
    return pythonCommunityPlugin { spec -> spec.bundlingRestrictions.marketplace = true }
  }

  /**
   * The layout of `com.intellij.python.basic`.
   *
   * [pythonCommunityPluginLayouts] registers it next to the `PythonCore` layout. The `PythonCore` main module depends on
   * [BASIC_PLUGIN_MAIN_MODULE_NAME], and the name has the `intellij.python.` prefix. So without this layout, the `auto`
   * `PythonCore` layout packs the module as its own child.
   */
  fun pythonBasicPlugin(): PluginLayout = PluginLayout.pluginAuto(listOf(BASIC_PLUGIN_MAIN_MODULE_NAME))

  fun pythonPlugin(mainModuleName: String, name: String, modules: List<String>, body: (PluginLayout.PluginLayoutSpec) -> Unit): PluginLayout {
    return PluginLayout.pluginAutoWithCustomDirName(mainModuleName, name) { spec ->
      spec.withModules(modules)
      if (mainModuleName == MAIN_MODULE_NAME) {
        spec.withResourceTree(
          moduleName = "intellij.python.helpers",
          resourcePath = "",
          relativeOutputPath = HELPERS_PLUGIN_DIR,
          excludedFiles = listOf("setup.py", "conftest.py"),
          excludedDirectories = listOf("tests", ".idea", "pydev/pydev_test*"),
        )
        spec.withResourceTree(
          moduleName = "intellij.python.bundledPyiStubs",
          resourcePath = "",
          relativeOutputPath = "bundledPyiStubs",
        )
        spec.withDistSourceTree(pydevSourceTree)
      }

      // required for "Python Console" in PythonCore plugin
      @Suppress("SpellCheckingInspection")
      body(spec)
    }
  }

  fun getPluginBuildNumber(): String = System.getProperty("build.number", "SNAPSHOT")
}
