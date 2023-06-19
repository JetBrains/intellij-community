// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.representer.Representer
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

class YamlInspectionProfileRaw(
  val baseProfile: String? = null,
  val name: String? = null,
  val groups: List<YamlInspectionGroupRaw> = emptyList(),
  val inspections: List<YamlInspectionConfigRaw> = emptyList()
)

class YamlInspectionGroupRaw(
  val groupId: String = "Unknown",
  val inspections: List<String> = emptyList(),
  val groups: List<String> = emptyList()
)

class YamlInspectionConfigRaw(
  val inspection: String? = null,
  val group: String? = null,
  val enabled: Boolean? = null,
  val severity: String? = null,
  val ignore: List<String> = emptyList(),
  val options: Map<String, String>? = null
)


fun readConfig(reader: Reader, includeReaders: (Path) -> Reader): YamlInspectionProfileRaw {
  val merged = readRaw(reader, includeReaders)
  val representer = Representer(DumperOptions())
  representer.propertyUtils.isSkipMissingProperties = true
  val constr = CustomClassLoaderConstructor(YamlInspectionProfileRaw::class.java, YamlInspectionProfileRaw::class.java.classLoader, LoaderOptions())
  val yaml = Yaml(constr, representer)
  yaml.setBeanAccess(BeanAccess.FIELD)

  return yaml.load(yaml.dump(merged))
}

private fun merge(first: Map<String, *>, second: Map<String, *>): Map<String, *> {
  return (first.keys + second.keys).associateWith { key ->
    val firstValue = first[key]
    val secondValue = second[key]
    val value = if (firstValue is Map<*, *> && secondValue is Map<*, *>) {
      @Suppress("UNCHECKED_CAST")
      merge(firstValue as Map<String, *>, secondValue as Map<String, *>)
    }
    else if (firstValue is List<*> && secondValue is List<*>) {
      secondValue + firstValue
    }
    else {
      secondValue ?: firstValue
    }
    value
  }
}

private val FIELDS_TO_MERGE = setOf("groups", "inspections")

private fun readRaw(reader: Reader, includeReaders: (Path) -> Reader): Map<String, *> {
  val yamlReader = Yaml()
  val rawConfig: Map<String, *> = yamlReader.load(reader)
  val includedConfigs = (rawConfig["include"] as? List<*>)?.filterIsInstance(String::class.java).orEmpty().map { Paths.get(it) }

  return includedConfigs.fold(rawConfig) { accumulator, path ->
    val includedYaml = readRaw(includeReaders.invoke(path)) { includeReaders.invoke(path.parent.resolve(it)) }
    merge(accumulator, includedYaml.filterKeys { field -> field in FIELDS_TO_MERGE })
  }
}