// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuiltinModulesFileData

import java.nio.file.Files
import java.nio.file.Path

fun readBuiltinModulesFile(file: Path): BuiltinModulesFileData {
  return Files.newInputStream(file).use { Json.decodeFromStream<BuiltinModulesFileData>(it) }
}

fun customizeBuiltinModulesAllowOnlySpecified(
  context: BuildContext,
  builtinModulesFile: Path,
  moduleNames: List<String>?,
  pluginNames: List<String>?,
  fileExtensions: List<String>?,
) {
  context.messages.info("File $builtinModulesFile before modification:\n" + Files.readString(builtinModulesFile))

  val objectMapper = ObjectMapper()

  val root = objectMapper.readTree(builtinModulesFile.toFile())
  if (moduleNames != null) {
    setArrayNodeElementsInBuiltinModules(builtinModulesFile, root, "modules", moduleNames)
  }

  if (pluginNames != null) {
    setArrayNodeElementsInBuiltinModules(builtinModulesFile, root, "plugins", pluginNames)
  }

  if (fileExtensions != null) {
    setArrayNodeElementsInBuiltinModules(builtinModulesFile, root, "fileExtensions", fileExtensions)
  }

  Files.write(builtinModulesFile, objectMapper.writeValueAsBytes(root))

  context.messages.info("File $builtinModulesFile AFTER modification:\n" + Files.readString(builtinModulesFile))
}

private fun setArrayNodeElementsInBuiltinModules(file: Path,
                                                 root: JsonNode,
                                                 sectionName: String,
                                                 valueList: List<String>) {
  val node = root.get(sectionName) as ArrayNode?
             ?: throw IllegalStateException("'$sectionName' was not found in $file:\n" + Files.readString(file))

  val existingValues = node.map { it.asText() }
  node.removeAll()
  for (value in valueList) {
    check(existingValues.contains(value)) {
      "Value '$value' in '$sectionName' was not found across existing values in $file:\n" + Files.readString(file)
    }
    node.add(value)
  }
}
