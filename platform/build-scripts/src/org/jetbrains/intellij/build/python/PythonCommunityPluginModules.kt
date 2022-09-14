// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.io.copyDir
import java.nio.file.Files

object PythonCommunityPluginModules {
  @JvmStatic
  val COMMUNITY_MODULES: PersistentList<String> = persistentListOf(
    "intellij.python.community",
    "intellij.python.community.plugin.impl",
    "intellij.python.community.plugin.java",
    "intellij.python.psi",
    "intellij.python.psi.impl",
    "intellij.python.pydev",
    "intellij.python.community.impl",
    "intellij.python.langInjection",
    "intellij.python.copyright",
    "intellij.python.terminal",
    "intellij.python.grazie",
    "intellij.python.markdown",
    "intellij.python.reStructuredText",
    "intellij.python.sdk",
    "intellij.python.featuresTrainer",
    "intellij.jupyter.core"
  )

  const val pythonCommunityName = "python-ce"

  fun pythonCommunityPluginLayout(body: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    val communityOnlyModules = persistentListOf(
      "intellij.python.community.plugin",
      "intellij.python.community.plugin.minor",
    )
    return pythonPlugin("intellij.python.community.plugin", pythonCommunityName, COMMUNITY_MODULES.addAll(communityOnlyModules)) { spec ->
      body?.invoke(spec)
    }
  }

  @JvmStatic
  fun pythonPlugin(mainModuleName: String,
                   name: String,
                   modules: List<String>,
                   body: (PluginLayout.PluginLayoutSpec) -> Unit): PluginLayout {
    return PluginLayout.plugin(mainModuleName) { spec ->
      spec.directoryName = name
      spec.mainJarName = "${name}.jar"
      spec.withModules(modules)
      spec.withModule(mainModuleName)
      spec.withGeneratedResources { targetDir, context ->
        val output = targetDir.resolve("helpers")
        Files.createDirectories(output)
        copyDir(
          sourceDir = context.paths.communityHomeDir.communityRoot.resolve("python/helpers"), targetDir = output,
          dirFilter = { path ->
            when {
              path.endsWith("tests") || path.endsWith(".idea") -> false
              path.parent?.fileName?.toString() == "pydev" -> !path.fileName.toString().startsWith("pydev_test")
              else -> true
            }
          },
          fileFilter = { path -> !path.endsWith("setup.py") }
        )
      }
      // required for "Python Console" in intellij.python.community.impl module
      @Suppress("SpellCheckingInspection")
      spec.withProjectLibrary("libthrift")
      body(spec)
    }
  }

  @JvmStatic
  fun getPluginBuildNumber(): String = System.getProperty("build.number", "SNAPSHOT")
}