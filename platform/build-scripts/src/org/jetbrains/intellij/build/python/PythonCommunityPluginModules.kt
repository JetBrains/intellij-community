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
    "intellij.python.community",
    "intellij.python.community.plugin.impl",
    "intellij.python.community.plugin.java",
    "intellij.python.psi",
    "intellij.python.psi.impl",
    "intellij.python.community.core.impl",
    "intellij.python.pydev",
    "intellij.python.community.impl",
    "intellij.python.community.impl.huggingFace",
    "intellij.python.community.communityOnly",
    "intellij.python.langInjection",
    "intellij.python.copyright",
    "intellij.python.terminal",
    "intellij.python.grazie",
    "intellij.python.markdown",
    "intellij.python.reStructuredText",
    "intellij.commandInterface",
    "intellij.python.sdk",
    "intellij.python.featuresTrainer",
    "intellij.jupyter.core",
    "intellij.python.community.deprecated.extensions"
  )

  /**
   * List of modules used in both Python plugin and Python Frontend plugin
   */
  @JvmStatic
  val PYTHON_COMMON_MODULES: PersistentList<String> = persistentListOf(
    "intellij.python.parser",
    "intellij.python.ast",
    "intellij.python.syntax",
    "intellij.python.syntax.core"
  )

  const val pythonCommunityName: String = "python-ce"

  fun pythonCommunityPluginLayout(body: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    val communityOnlyModules = persistentListOf(
      "intellij.python.community.plugin.minor",
      "intellij.python.community.plugin.minorRider",
    )
    return pythonPlugin("intellij.python.community.plugin", pythonCommunityName, COMMUNITY_MODULES + communityOnlyModules) { spec ->
      body?.invoke(spec)
      spec.withProjectLibrary("XmlRPC")
    }
  }

  fun pythonPlugin(mainModuleName: String, name: String, modules: List<String>, body: (PluginLayout.PluginLayoutSpec) -> Unit): PluginLayout {
    return PluginLayout.plugin(mainModuleName, auto = true) { spec ->
      spec.directoryName = name
      spec.mainJarName = "$name.jar"
      spec.withModules(modules)
      PYTHON_COMMON_MODULES.forEach { 
        spec.withModule(it, "python-common.jar")
      }
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
      // required for "Python Console" in intellij.python.community.impl module
      @Suppress("SpellCheckingInspection")
      spec.withProjectLibrary("libthrift")
      spec.excludeProjectLibrary("Gradle")
      body(spec)
    }
  }

  fun getPluginBuildNumber(): String = System.getProperty("build.number", "SNAPSHOT")
}