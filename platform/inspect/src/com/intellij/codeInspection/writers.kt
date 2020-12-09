// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE
import org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE
import org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE
import org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun writeProjectDescription(path: Path, project: Project) {
  val gson = GsonBuilder().setPrettyPrinting().create()
  val writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)
  writer.use {
    val jsonWriter = gson.newJsonWriter(writer)
    writeProjectDescription(project, jsonWriter)
  }
}

private fun writeProjectDescription(project: Project, writer: JsonWriter) {
  val modules = ModuleManager.getInstance(project).modules
  val macroManager = PathMacroManager.getInstance(project)

  //all modules
  writer.beginObject()
  writer.name("modules")
  writer.beginArray()
  for (module in modules) {
    writeModuleDescription(module, writer, macroManager)
  }
  writer.endArray()

  //all orderEntries
  writer.name("orderEntries")
  writer.beginArray()
  val entries = mutableListOf<OrderEntry>()

  ProjectRootManager.getInstance(project).orderEntries().withoutModuleSourceEntries().forEach {
    entries.add(it)
    true
  }

  entries.distinctBy { it.presentableName }.forEach {
    writeOrderEntry(it, writer, macroManager)
  }
  writer.endArray()
  writer.endObject()
}

private fun writeModuleDescription(module: Module,
                                   writer: JsonWriter,
                                   macroManager: PathMacroManager) {
  val rootManager = ModuleRootManager.getInstance(module)

  writer.beginObject()
  writer.name("Name").value(module.name)
  writer.name("ContentEntries").beginArray()
  for (contentEntry in rootManager.contentEntries) {
    writeContentEntry(contentEntry, writer, macroManager)
  }
  writer.endArray()
  writer.name("OrderEntries").beginArray()
  for (orderEntry in rootManager.orderEntries) {
    writer.value(orderEntry.presentableName)
  }
  writer.endArray()
  writer.endObject()
}

private fun writeContentEntry(contentEntry: ContentEntry,
                              writer: JsonWriter,
                              macroManager: PathMacroManager) {
  writer.beginObject()
  writer.name("Path").value(macroManager.collapsePath(contentEntry.url))
  writer.name("SourceFolders").beginArray()
  for (sourceFolder in contentEntry.sourceFolders) {
    writer.beginObject()
    writer.name("Path").value(macroManager.collapsePath(sourceFolder.url))
    writer.name("Type").value(sourceFolder.type())
    writer.endObject()
  }
  for (excludeFolder in contentEntry.excludeFolders) {
    writer.beginObject()
    writer.name("Path").value(macroManager.collapsePath(excludeFolder.url))
    writer.name("Type").value("Exclude")
    writer.endObject()
  }
  writer.endArray()
  writer.endObject()
}

private fun writeOrderEntry(orderEntry: OrderEntry,
                            writer: JsonWriter,
                            macroManager: PathMacroManager) {
  writer.beginObject()
  writer.name("Name").value(orderEntry.presentableName)
  writer.name("Roots").beginArray()
  for (root in orderEntry.getUrls(OrderRootType.CLASSES)) {
    writer.beginObject()
    writer.name("Path").value(macroManager.collapsePath(root))
    writer.endObject()
  }
  writer.endArray()
  writer.endObject()
}

private fun SourceFolder.type(): String {
  return when (rootType) {
    RESOURCE -> "Resource"
    TEST_RESOURCE -> "TestResource"
    TEST_SOURCE -> "TestSource"
    SOURCE -> {
      if (jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)?.isForGeneratedSources == true) "GeneratedSource"
      else "Source"
    }
    else -> "Source"
  }
}
