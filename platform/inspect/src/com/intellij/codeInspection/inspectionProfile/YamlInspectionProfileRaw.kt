// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.inspectionProfile.YamlProfileUtils.makeYaml
import org.yaml.snakeyaml.Yaml
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

internal class YamlInspectionProfileRaw(
  val baseProfile: String? = null,
  val name: String? = null,
  val groups: List<YamlInspectionGroupRaw> = emptyList(),
  val inspections: List<YamlInspectionConfigRaw> = emptyList()
) {
  fun dump(): String {
    val yaml = makeYaml()
    return yaml.dump(this)
  }
}

internal class YamlInspectionGroupRaw(
  val groupId: String = "Unknown",
  val inspections: List<String> = emptyList(),
  val groups: List<String> = emptyList()
)

internal class YamlInspectionConfigRaw(
  val inspection: String? = null,
  val group: String? = null,
  val enabled: Boolean? = null,
  val severity: String? = null,
  val ignore: List<String> = emptyList(),
  val options: Map<String, String>? = null
)

internal fun readConfig(reader: Reader, includeReaders: (Path) -> Reader): YamlInspectionProfileRaw {
  val merged = readRaw(reader, includeReaders)
  val yaml = makeYaml()

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
  val includedConfigs = (rawConfig["include"] as? List<*>)
    ?.filterIsInstance<String>()
    .orEmpty()
    .map { Paths.get(it) }
    .asReversed() // Values from included placed at the beginning of config. So the first in the list should be added last to keep order.

  return includedConfigs.fold(rawConfig) { accumulator, path ->
    val includedYaml = includeReaders.invoke(path).use { includeReader ->
      readRaw(includeReader) { includeReaders.invoke(path.resolveSibling(it)) }
    }
    merge(accumulator, includedYaml.filterKeys { field -> field in FIELDS_TO_MERGE })
  }
}