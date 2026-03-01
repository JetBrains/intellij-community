// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.intellij.platform.pluginGraph.PluginGraph
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetModuleNames
import tools.jackson.core.JsonGenerator

@Serializable
internal data class ModuleSetEntry(
  val name: String,
  val location: String,
  val sourceFile: String,
  val directModules: List<ContentModule>,
  val directNestedSets: List<String>,
  val alias: String? = null,
  val selfContained: Boolean = false,
  val allModulesFlattened: List<String>
)

private fun buildModuleSetEntry(
  moduleSet: ModuleSet,
  location: String,
  sourceFilePath: String,
  pluginGraph: PluginGraph
): ModuleSetEntry {
  return ModuleSetEntry(
    name = moduleSet.name,
    location = location,
    sourceFile = sourceFilePath,
    directModules = moduleSet.modules,
    directNestedSets = moduleSet.nestedSets.map { it.name },
    alias = moduleSet.alias?.value,
    selfContained = moduleSet.selfContained,
    allModulesFlattened = collectModuleSetModuleNames(pluginGraph, moduleSet.name)
      .map { it.value }
      .sorted()
  )
}

/**
 * Writes a single module set to JSON using kotlinx.serialization.
 */
internal fun writeModuleSet(
  gen: JsonGenerator,
  moduleSet: ModuleSet,
  location: String,
  sourceFilePath: String,
  pluginGraph: PluginGraph
) {
  val entry = buildModuleSetEntry(moduleSet, location, sourceFilePath, pluginGraph)
  gen.writeRawValue(kotlinxJson.encodeToString(entry))
}

/**
 * Writes a name-indexed map of module sets for O(1) lookup on the client side.
 */
internal fun writeModuleSetIndex(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph
) {
  val entries = LinkedHashMap<String, ModuleSetEntry>()
  for ((moduleSet, location, sourceFilePath) in allModuleSets.sortedBy { it.moduleSet.name }) {
    entries[moduleSet.name] = buildModuleSetEntry(moduleSet, location.name, sourceFilePath, pluginGraph)
  }
  gen.writeRawValue(kotlinxJson.encodeToString(entries))
}

/**
 * Writes duplicate analysis section including both module duplicates and xi:include duplicates.
 */
internal fun writeDuplicateAnalysis(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph
) {
  @Serializable
  data class ModuleDuplicate(val moduleName: String, val appearsInSets: List<String>)

  @Serializable
  data class SetOverlap(
    val set1: String,
    val set2: String,
    val overlapCount: Int,
    val set1TotalModules: Int,
    val set2TotalModules: Int,
    val overlapPercentage: Double,
    val uniqueToSet1: List<String>? = null,
    val uniqueToSet2: List<String>? = null
  )

  // Find modules that appear in multiple module sets (using cache for O(1) lookups)
  val moduleToSets = LinkedHashMap<String, MutableList<String>>()
  for (entry in allModuleSets) {
    val allModules = collectModuleSetModuleNames(pluginGraph, entry.moduleSet.name)
    for (moduleName in allModules) {
      moduleToSets.computeIfAbsent(moduleName.value) { ArrayList() }.add(entry.moduleSet.name)
    }
  }

  val modulesInMultipleSets = moduleToSets
    .filter { it.value.size > 1 }
    .entries
    .sortedBy { it.key }
    .map { (moduleName, setNames) -> ModuleDuplicate(moduleName, setNames.sorted()) }

  gen.writeName("modulesInMultipleSets")
  gen.writeRawValue(kotlinxJson.encodeToString(modulesInMultipleSets))

  // Set overlap analysis (using cache for O(1) lookups)
  val setOverlapAnalysis = mutableListOf<SetOverlap>()
  for (i in allModuleSets.indices) {
    for (j in i + 1 until allModuleSets.size) {
      val (set1, _, _) = allModuleSets[i]
      val (set2, _, _) = allModuleSets[j]

      val modules1 = collectModuleSetModuleNames(pluginGraph, set1.name)
      val modules2 = collectModuleSetModuleNames(pluginGraph, set2.name)

      val overlap = modules1.intersect(modules2)
      if (overlap.size > 5) { // Only report significant overlaps
      val uniqueToSet1 = (modules1 - modules2).map { it.value }.sorted().take(10).ifEmpty { null }
      val uniqueToSet2 = (modules2 - modules1).map { it.value }.sorted().take(10).ifEmpty { null }
        val overlapPercentage = (overlap.size.toDouble() / minOf(modules1.size, modules2.size)) * 100

        setOverlapAnalysis.add(SetOverlap(
          set1 = set1.name,
          set2 = set2.name,
          overlapCount = overlap.size,
          set1TotalModules = modules1.size,
          set2TotalModules = modules2.size,
          overlapPercentage = overlapPercentage,
          uniqueToSet1 = uniqueToSet1,
          uniqueToSet2 = uniqueToSet2
        ))
      }
    }
  }

  gen.writeName("setOverlapAnalysis")
  gen.writeRawValue(kotlinxJson.encodeToString(setOverlapAnalysis))
}
