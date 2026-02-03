// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.name

sealed class RunConfigurationProperties(
  val name: String,
  val moduleName: String,
  val vmParameters: List<String>,
  val envVariables: Map<String, String>,
) {
  companion object {
    private val whitespaceRegex = Regex("\\s+")

    internal fun getVmParameters(options: Map<String, String?>): List<String> {
      return (options.get("VM_PARAMETERS") ?: "-ea").split(whitespaceRegex)
    }

    internal fun getModuleName(configuration: XmlElement): String {
      return (configuration.getChild("module")?.getAttributeValue("name")
              ?: throw RuntimeException("Cannot run configuration: module name is not specified"))
    }

    internal fun getEnv(configuration: XmlElement): Map<String, String> {
      return configuration.getChild("envs")
               ?.children("env")
               ?.associate { it.getAttributeValue("name")!! to it.getAttributeValue("value")!! }
             ?: emptyMap()
    }
  }
}

internal fun getRunConfiguration(file: Path): XmlElement {
  val root = file.inputStream().use(::readXmlAsModel)
  return requireNotNull(root.getChild("configuration")) { "Cannot load configuration from '${file.name}': 'configuration' tag is not found" }
}

internal fun loadRunConfigurations(name: String, projectHome: Path): List<JUnitRunConfigurationProperties> {
  val file = findRunConfiguration(projectHome, name)
  val configuration = getRunConfiguration(file)
  return when (val type = getConfigurationType(configuration)) {
    JUnitRunConfigurationProperties.TYPE -> {
      listOf(JUnitRunConfigurationProperties.loadRunConfiguration(file))
    }
    CompoundRunConfigurationProperties.TYPE -> {
      val runConfiguration = CompoundRunConfigurationProperties.loadRunConfiguration(file)
      runConfiguration.toRun.flatMap { name -> loadRunConfigurations(name, projectHome) }
    }
    else -> {
      throw RuntimeException("Unsupported run configuration type '${type}' in run configuration '${name}' of project '${projectHome}'")
    }
  }
}

private fun findRunConfiguration(projectHome: Path, name: String): Path {
  val file = projectHome.resolve(".idea/runConfigurations/${FileUtil.sanitizeFileName(name)}.xml")
  if (Files.notExists(file)) {
    throw RuntimeException("Cannot find run configurations: file '${projectHome.relativize(file)}' does not exist")
  }
  return file
}

private fun getConfigurationType(configuration: XmlElement): String {
  return requireNotNull(configuration.getAttributeValue("type")) { "Cannot load configuration: 'type' attribute is missing: ${configuration}" }
}