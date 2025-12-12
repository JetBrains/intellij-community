// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import java.nio.file.Path
import kotlin.io.path.name

class JUnitRunConfigurationProperties private constructor(
  name: String,
  moduleName: String,
  val testSearchScope: TestSearchScope,
  val testClassPatterns: List<String>,
  vmParameters: List<String>,
  envVariables: Map<String, String>,
  val buildProject: Boolean,
) : RunConfigurationProperties(name, moduleName, vmParameters, envVariables) {
  enum class TestSearchScope(val serialized: String) {
    WHOLE_PROJECT("wholeProject"),
    SINGLE_MODULE("singleModule"),
    MODULE_WITH_DEPENDENCIES("moduleWithDependencies"),
  }

  companion object {
    internal const val TYPE = "JUnit"

    fun loadRunConfiguration(file: Path): JUnitRunConfigurationProperties {
      val configuration = getRunConfiguration(file)
      if (!configuration.getAttributeValue("type").equals(TYPE)) {
        throw RuntimeException("Cannot load configuration from '${file.name}': only JUnit run configuration are supported")
      }

      val moduleName = getModuleName(configuration)
      val options = configuration.children("option").associate { it.getAttributeValue("name")!! to it.getAttributeValue("value") }
      val testKind = options.get("TEST_OBJECT") ?: "class"
      val testClassPatterns = when (testKind) {
        "class" -> listOf(options.get("MAIN_CLASS_NAME")!!)
        "package" -> listOf("${options.get("PACKAGE_NAME")!!}.*")
        "pattern" -> configuration.getChild("patterns")!!.children("pattern").map { it.getAttributeValue("testClass")!! }.toList()
        else -> throw RuntimeException("Cannot run ${file.name} configuration: '$testKind' test kind is not supported")
      }

      val forkMode = configuration.getChild("fork_mode")?.getAttributeValue("value")
      if (forkMode != null && forkMode != "none") {
        throw RuntimeException("Cannot run ${file.name} configuration: fork mode '$forkMode' is not supported")
      }

      val vmParameters = getVmParameters(options) + (if ("pattern" == testKind) listOf("-Dintellij.build.test.patterns.escaped=true") else emptyList())
      val envVariables = getEnv(configuration)

      val scopeSerialized: String = configuration.getChild("TEST_SEARCH_SCOPE")?.getChild("value")?.getAttributeValue("defaultValue")
                                    ?: TestSearchScope.MODULE_WITH_DEPENDENCIES.serialized
      val testSearchScope = TestSearchScope.entries.firstOrNull { it.serialized == scopeSerialized } ?: error(
        "TEST_SEARCH_SCOPE value '$scopeSerialized' must be one of ${TestSearchScope.entries.map { it.serialized }}")

      val buildProject = configuration.getChild("method")
        ?.children("option")
        ?.any {
          it.getAttributeValue("name") == "MakeProject" &&
          it.getAttributeValue("enabled") == "true"
        } == true

      return JUnitRunConfigurationProperties(
        name = configuration.getAttributeValue("name")!!,
        moduleName = moduleName,
        testSearchScope = testSearchScope,
        testClassPatterns = testClassPatterns,
        vmParameters = vmParameters,
        envVariables = envVariables,
        buildProject = buildProject,
      )
    }
  }
}