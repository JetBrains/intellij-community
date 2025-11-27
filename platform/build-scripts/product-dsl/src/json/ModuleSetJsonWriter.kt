// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import org.jetbrains.intellij.build.productLayout.DuplicateIncludeDetector
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.analysis.ProductSpec
import org.jetbrains.intellij.build.productLayout.collectAllModuleNames
import org.jetbrains.intellij.build.productLayout.collectAllModuleNamesFromSet
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Writes a single module set to JSON.
 * Uses kotlinx.serialization to serialize the ModuleSet structure directly,
 * then embeds the raw JSON using writeRawValue().
 */
fun writeModuleSet(
  gen: JsonGenerator,
  moduleSet: ModuleSet,
  location: String,
  sourceFilePath: String,
  allModuleSets: List<ModuleSet>
) {
  gen.writeStartObject()

  // Metadata fields
  gen.writeStringField("name", moduleSet.name)
  gen.writeStringField("location", location)
  gen.writeStringField("sourceFile", sourceFilePath)

  // Serialize ModuleSet using kotlinx.serialization and write raw JSON
  val moduleSetJson = kotlinxJson.encodeToString(moduleSet)
  gen.writeFieldName("moduleSet")
  gen.writeRawValue(moduleSetJson)

  // Add flattened list of all modules (including from nested sets)
  gen.writeArrayFieldStart("allModulesFlattened")
  val allModules = collectAllModuleNamesFromSet(allModuleSets, moduleSet.name)
  for (moduleName in allModules.sorted()) {
    gen.writeString(moduleName)
  }
  gen.writeEndArray()

  gen.writeEndObject()
}

/**
 * Writes duplicate analysis section including both module duplicates and xi:include duplicates.
 */
fun writeDuplicateAnalysis(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path
) {
  // Find modules that appear in multiple module sets
  val moduleToSets = mutableMapOf<String, MutableList<String>>()
  for ((moduleSet, _, _) in allModuleSets) {
    val allModules = collectAllModuleNames(moduleSet)
    for (moduleName in allModules) {
      moduleToSets.computeIfAbsent(moduleName) { mutableListOf() }.add(moduleSet.name)
    }
  }

  val duplicateModules = moduleToSets.filter { it.value.size > 1 }

  gen.writeArrayFieldStart("modulesInMultipleSets")
  for ((moduleName, setNames) in duplicateModules.entries.sortedBy { it.key }) {
    gen.writeStartObject()
    gen.writeStringField("moduleName", moduleName)

    gen.writeArrayFieldStart("appearsInSets")
    for (setName in setNames.sorted()) {
      gen.writeString(setName)
    }
    gen.writeEndArray()

    gen.writeEndObject()
  }
  gen.writeEndArray()

  // Set overlap analysis
  gen.writeArrayFieldStart("setOverlapAnalysis")
  for (i in allModuleSets.indices) {
    for (j in i + 1 until allModuleSets.size) {
      val (set1, _, _) = allModuleSets[i]
      val (set2, _, _) = allModuleSets[j]

      val modules1 = collectAllModuleNames(set1)
      val modules2 = collectAllModuleNames(set2)

      val overlap = modules1.intersect(modules2)
      if (overlap.size > 5) { // Only report significant overlaps
        val uniqueToSet1 = modules1 - modules2
        val uniqueToSet2 = modules2 - modules1

        gen.writeStartObject()
        gen.writeStringField("set1", set1.name)
        gen.writeStringField("set2", set2.name)
        gen.writeNumberField("overlapCount", overlap.size)
        gen.writeNumberField("set1TotalModules", modules1.size)
        gen.writeNumberField("set2TotalModules", modules2.size)
        
        val overlapPercentage = (overlap.size.toDouble() / minOf(modules1.size, modules2.size)) * 100
        gen.writeNumberField("overlapPercentage", overlapPercentage)

        if (uniqueToSet1.isNotEmpty()) {
          gen.writeArrayFieldStart("uniqueToSet1")
          for (moduleName in uniqueToSet1.sorted().take(10)) { // Limit to first 10
            gen.writeString(moduleName)
          }
          gen.writeEndArray()
        }

        if (uniqueToSet2.isNotEmpty()) {
          gen.writeArrayFieldStart("uniqueToSet2")
          for (moduleName in uniqueToSet2.sorted().take(10)) { // Limit to first 10
            gen.writeString(moduleName)
          }
          gen.writeEndArray()
        }

        gen.writeEndObject()
      }
    }
  }
  gen.writeEndArray()
  
  // xi:include duplicate detection
  val productFiles = products
    .mapNotNull { it.pluginXmlPath }
    .map { projectRoot.resolve(it) }
    .filter { it.exists() && it.isRegularFile() }
  
  val report = DuplicateIncludeDetector.detectDuplicates(productFiles, projectRoot)
  
  // Serialize using kotlinx.serialization and write raw JSON (consistent with ModuleSet pattern)
  val reportJson = kotlinxJson.encodeToString(report)
  gen.writeFieldName("xiIncludeDuplicates")
  gen.writeRawValue(reportJson)
}
