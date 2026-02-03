// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.platform.buildData.productInfo.ProductInfoLayoutItemKind
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.BuiltinModulesFileData
import java.nio.file.Files
import java.nio.file.Path

fun readBuiltinModulesFile(file: Path): BuiltinModulesFileData {
  return Files.newInputStream(file).use { Json.decodeFromStream<BuiltinModulesFileData>(it) }
}

fun customizeBuiltinModulesAllowOnlySpecified(
  builtinModulesFile: Path,
  pluginAliases: Set<String>?,
  pluginNames: List<String>?,
  fileExtensions: List<String>?,
) {
  Span.current().addEvent("File $builtinModulesFile before modification:\n" + Files.readString(builtinModulesFile))

  val root = readBuiltinModulesFile(builtinModulesFile)
  if (pluginAliases != null) {
    val existingValues = root.layout.associateByTo(HashMap(root.layout.size)) { it.name }
    for (name in pluginAliases) {
      val item = existingValues.get(name)
      requireNotNull(item) {
        "Value '$name' in '$pluginAliases' was not found across existing values in $builtinModulesFile:\n" + Files.readString(builtinModulesFile)
      }
    }
    root.layout = root.layout.filter {
      if (it.kind == ProductInfoLayoutItemKind.pluginAlias) pluginAliases.contains(it.name) else true
    }
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