// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.io.copyDir
import java.nio.file.Files

object PythonCommunityPluginModules {
  @JvmField
  val COMMUNITY_MODULES: PersistentList<String> = persistentListOf(
    "intellij.commandInterface",
    "intellij.python.community",
    "intellij.python.community.communityOnly",
    "intellij.python.community.core.impl",
    "intellij.python.community.impl",
    "intellij.python.community.impl.huggingFace",
    "intellij.python.community.plugin.impl",
    "intellij.python.community.plugin.java",
    "intellij.python.community.plugin.minor",
    "intellij.python.community.plugin.minorRider",
    "intellij.python.copyright",
    "intellij.python.featuresTrainer",
    "intellij.python.grazie",
    "intellij.python.langInjection",
    "intellij.python.markdown",
    "intellij.python.psi",
    "intellij.python.psi.impl",
    "intellij.python.pydev",
    "intellij.python.sdk",
    "intellij.python.terminal",
  )

  /**
   * List of modules used in both Python plugin and Python Frontend plugin
   */
  @JvmField
  val PYTHON_COMMON_MODULES: PersistentList<String> = persistentListOf(
    "intellij.python.parser",
    "intellij.python.ast",
    "intellij.python.syntax",
    "intellij.python.syntax.core"
  )

  const val pythonCommunityName: String = "python-ce"

  fun pythonCommunityPluginLayout(body: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return pythonPlugin("intellij.python.community.plugin", pythonCommunityName, COMMUNITY_MODULES) { spec ->
      PYTHON_COMMON_MODULES.forEach {
        spec.withModule(it, "python-common.jar")
      }

      body?.invoke(spec)
      spec.withProjectLibrary("XmlRPC")
    }
  }

  fun pythonPlugin(mainModuleName: String, name: String, modules: List<String>, body: (PluginLayout.PluginLayoutSpec) -> Unit): PluginLayout {
    return PluginLayout.pluginAutoWithDeprecatedCustomDirName(mainModuleName) { spec ->
      spec.directoryName = name
      spec.mainJarName = "$name.jar"
      spec.withModules(modules)
      if (mainModuleName == "intellij.python.community.plugin") {
        spec.withGeneratedResources { targetDir, context ->
          val output = targetDir.resolve("helpers")
          Files.createDirectories(output)
          copyDir(
            sourceDir = context.paths.communityHomeDir.resolve("python/helpers"), targetDir = output,
            dirFilter = { path ->
              when {
                path.endsWith("tests") || path.endsWith(".idea") -> false
                path.parent?.fileName?.toString() == "pydev" -> !path.fileName.toString().startsWith("pydev_test")
                else -> true
              }
            },
            fileFilter = { path -> !path.endsWith("setup.py") && !path.endsWith("conftest.py") }
          )
        }
      }

      // required for "Python Console" in PythonCore plugin
      @Suppress("SpellCheckingInspection")
      spec.withProjectLibrary("libthrift")
      spec.excludeProjectLibrary("Gradle")
      body(spec)
    }
  }

  fun getPluginBuildNumber(): String = System.getProperty("build.number", "SNAPSHOT")
}
