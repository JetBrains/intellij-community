// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.application.PathManager
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import kotlin.io.path.exists
import kotlin.io.path.readText

@ApiStatus.Internal
data class DevBuildServerSettings(
  val name: String,
  val mainClassModule: String,
  val mainClass: String,
  val enabledForModules: List<String>,
  val disabledForModules: List<String>,
  val jvmArgs: List<String>,
  val envs: List<String>,
) {
  companion object {
    private val runners: List<DevBuildServerSettings> by lazy {
      var intellijYaml = COMMUNITY_ROOT.communityRoot.parent.resolve("intellij.yaml")
      if (!intellijYaml.exists()) {
        intellijYaml = COMMUNITY_ROOT.communityRoot.resolve("intellij.yaml")
      }
      if (!intellijYaml.exists()) {
        return@lazy emptyList()
      }
      val yamlRoot = ObjectMapper(YAMLFactory()).readTree(intellijYaml.readText())
      val result = mutableListOf<DevBuildServerSettings>()
      for (runner in yamlRoot.path("unitTesting").path("runners").values()) {
        result.add(
          DevBuildServerSettings(
            name = runner.path("name").asString(),
            mainClassModule = runner.path("mainClassModule").asString(),
            mainClass = runner.path("mainClass").asString(),
            enabledForModules = runner.path("enabledForModules").values().map(JsonNode::asString),
            disabledForModules = runner.path("disabledForModules").values().map(JsonNode::asString),
            jvmArgs = runner.path("jvmArgs").values().map(JsonNode::asString),
            envs = runner.path("envs").values().map(JsonNode::asString),
          )
        )
      }
      result
    }

    fun readDevBuildServerSettingsFromIntellijYaml(moduleName: String): DevBuildServerSettings? {
      val result = runners.filter { settings ->
        settings.enabledForModules.any { pattern -> matchesName(moduleName, pattern) } &&
        settings.disabledForModules.none { pattern -> matchesName(moduleName, pattern) }
      }
      check(result.size < 2) {
        "More than one runner for module '${moduleName}' in intellij.yaml"
      }
      return result.singleOrNull()
    }

    private fun matchesName(moduleName: String, pattern: String) =
      if (pattern.lastOrNull() == '*') {
        moduleName.startsWith(pattern.substring(0, pattern.length - 1))
      }
      else {
        moduleName == pattern
      }
  }

  fun apply(mainClass: String, mainModule: String, args: MutableList<String>, environment: MutableMap<String, String>) {
    environment.putAll(envs.map {
      val (key, value) = it.split('=', limit = 2)
      key to value.replacePlaceholders(mainModule)
    })

    args.addAll(jvmArgs.map {
      it.replacePlaceholders(mainModule)
    })
    val entryPointClass = System.getProperty("idea.dev.build.test.entry.point.class").nullize(nullizeSpaces = true) ?: mainClass
    args.add("-Didea.dev.build.test.entry.point.class=$entryPointClass")
    args.add(this.mainClass)
  }

  private fun String.replacePlaceholders(mainModule: String) =
    this.replace("${'$'}TEST_MODULE_NAME$", mainModule)
      .replace("${'$'}TEST_PROJECT_BASE_PATH$", PathManager.getHomeDir().toString())
}
