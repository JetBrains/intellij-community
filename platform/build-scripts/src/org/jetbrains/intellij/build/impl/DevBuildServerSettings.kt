// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.intellij.openapi.application.PathManager
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
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
      yamlRoot.path("unitTesting").path("runners").map {
        DevBuildServerSettings(
          name = it.path("name").asText(),
          mainClassModule = it.path("mainClassModule").asText(),
          mainClass = it.path("mainClass").asText(),
          enabledForModules = it.path("enabledForModules").map(JsonNode::asText),
          disabledForModules = it.path("disabledForModules").map(JsonNode::asText),
          jvmArgs = it.path("jvmArgs").map(JsonNode::asText),
          envs = it.path("envs").map(JsonNode::asText),
        )
      }
    }

    fun readDevBuildServerSettingsFromIntellijYaml(moduleName: String): DevBuildServerSettings? {
      val result = runners.filter {
        it.enabledForModules.any { matchesName(moduleName, it) } && it.disabledForModules.none { matchesName(moduleName, it) }
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
