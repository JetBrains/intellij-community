// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.io.copyDir
import java.nio.file.Files

object PythonCommunityPluginModules {

  const val pythonCommunityName: String = "python-ce"

  fun pythonCommunityPluginLayout(body: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return pythonPlugin("intellij.python.community.plugin", pythonCommunityName, emptyList()) { spec ->
      body?.invoke(spec)
      spec.withProjectLibrary("XmlRPC")
    }
  }

  fun pythonPlugin(mainModuleName: String, name: String, modules: List<String>, body: (PluginLayout.PluginLayoutSpec) -> Unit): PluginLayout {
    return PluginLayout.pluginAutoWithCustomDirName(mainModuleName, name) { spec ->
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
