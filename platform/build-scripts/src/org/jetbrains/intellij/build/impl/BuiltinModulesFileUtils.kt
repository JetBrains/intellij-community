// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.BuiltinModulesFileData
import org.jetbrains.intellij.build.ModuleDescriptor
import java.nio.file.Files
import java.nio.file.Path

fun readBuiltinModulesFile(file: Path): BuiltinModulesFileData {
  return Files.newInputStream(file).use { Json.decodeFromStream<BuiltinModulesFileData>(it) }
}

fun customizeBuiltinModulesAllowOnlySpecified(
  builtinModulesFile: Path,
  moduleNames: List<String>?,
  pluginNames: List<String>?,
  fileExtensions: List<String>?,
) {
  Span.current().addEvent("File $builtinModulesFile before modification:\n" + Files.readString(builtinModulesFile))

  val root = readBuiltinModulesFile(builtinModulesFile)
  if (moduleNames != null) {
    val existingValues = root.modules.associateByTo(HashMap(root.modules.size)) { it.name }
    val newList = ArrayList<ModuleDescriptor>(moduleNames.size)
    for (name in moduleNames) {
      val item = existingValues.get(name)
      requireNotNull(item) {
        "Value '$name' in '$moduleNames' was not found across existing values in $builtinModulesFile:\n" + Files.readString(builtinModulesFile)
      }
      newList.add(item)
    }
    root.modules = newList
  }

  if (pluginNames != null) {
    setArrayNodeElementsInBuiltinModules(builtinModulesFile, root.plugins, pluginNames)
  }

  if (fileExtensions != null) {
    setArrayNodeElementsInBuiltinModules(builtinModulesFile, root.fileExtensions, fileExtensions)
  }

  val result = Json.encodeToString<BuiltinModulesFileData>(root)
  Files.writeString(builtinModulesFile, result)
  Span.current().addEvent("file $builtinModulesFile AFTER modification:\n$result")
}

private fun setArrayNodeElementsInBuiltinModules(file: Path, list: MutableList<String>, valueList: List<String>) {
  val existingValues = HashSet(list)
  list.clear()
  for (value in valueList) {
    check(existingValues.contains(value)) {
      "Value '$value' in '$list' was not found across existing values in $file:\n" + Files.readString(file)
    }
    list.add(value)
  }
}