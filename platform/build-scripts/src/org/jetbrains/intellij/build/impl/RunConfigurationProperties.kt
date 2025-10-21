// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.name

abstract class RunConfigurationProperties(
  val name: String,
  val moduleName: String,
  val vmParameters: List<String>,
  val envVariables: Map<String, String>,
) {
  companion object {
    private val whitespaceRegex = Regex("\\s+")

    fun findRunConfiguration(projectHome: Path, name: String): Path {
      val file = projectHome.resolve(".idea/runConfigurations/${FileUtil.sanitizeFileName(name)}.xml")
      if (!file.exists()) {
        throw RuntimeException("Cannot find run configurations: file '${projectHome.relativize(file)}' does not exist")
      }
      return file
    }

    fun getConfiguration(file: Path): XmlElement {
      val root = file.inputStream().use(::readXmlAsModel)
      return root.getChild("configuration")
             ?: throw RuntimeException("Cannot load configuration from '${file.name}': 'configuration' tag is not found")
    }

    fun getConfigurationType(configuration: XmlElement): String {
      return configuration.getAttributeValue("type")
             ?: throw RuntimeException("Cannot load configuration: 'type' attribute is missing: ${configuration}")
    }

    fun getVmParameters(options: Map<String, String?>): List<String> {
      return (options.get("VM_PARAMETERS") ?: "-ea").split(whitespaceRegex)
    }

    fun getModuleName(configuration: XmlElement): String {
      return (configuration.getChild("module")?.getAttributeValue("name")
              ?: throw RuntimeException("Cannot run configuration: module name is not specified"))
    }

    fun getEnv(configuration: XmlElement): Map<String, String> {
      return configuration.getChild("envs")
               ?.children("env")
               ?.associate { it.getAttributeValue("name")!! to it.getAttributeValue("value")!! }
             ?: emptyMap()
    }
  }
}
