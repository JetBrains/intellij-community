// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.tooling.ProductSpec
import org.jetbrains.intellij.build.productLayout.tooling.detectDuplicates
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Writes a single module set to JSON using kotlinx.serialization.
 */
internal fun writeModuleSet(
  gen: JsonGenerator,
  moduleSet: ModuleSet,
  location: String,
  sourceFilePath: String,
  cache: ModuleSetTraversalCache
) {
  @Serializable
  data class ModuleSetEntry(
    val name: String,
    val location: String,
    val sourceFile: String,
    val moduleSet: ModuleSet,
    val allModulesFlattened: List<String>
  )

  val entry = ModuleSetEntry(
    name = moduleSet.name,
    location = location,
    sourceFile = sourceFilePath,
    moduleSet = moduleSet,
    allModulesFlattened = cache.getModuleNames(moduleSet.name).sorted()
  )
  gen.writeRawValue(kotlinxJson.encodeToString(entry))
}

/**
 * Writes duplicate analysis section including both module duplicates and xi:include duplicates.
 */
internal fun writeDuplicateAnalysis(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  cache: ModuleSetTraversalCache
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
  val moduleToSets = mutableMapOf<String, MutableList<String>>()
  for ((moduleSet, _, _) in allModuleSets) {
    val allModules = cache.getModuleNames(moduleSet)
    for (moduleName in allModules) {
      moduleToSets.computeIfAbsent(moduleName) { mutableListOf() }.add(moduleSet.name)
    }
  }

  val modulesInMultipleSets = moduleToSets
    .filter { it.value.size > 1 }
    .entries
    .sortedBy { it.key }
    .map { (moduleName, setNames) -> ModuleDuplicate(moduleName, setNames.sorted()) }

  gen.writeFieldName("modulesInMultipleSets")
  gen.writeRawValue(kotlinxJson.encodeToString(modulesInMultipleSets))

  // Set overlap analysis (using cache for O(1) lookups)
  val setOverlapAnalysis = mutableListOf<SetOverlap>()
  for (i in allModuleSets.indices) {
    for (j in i + 1 until allModuleSets.size) {
      val (set1, _, _) = allModuleSets[i]
      val (set2, _, _) = allModuleSets[j]

      val modules1 = cache.getModuleNames(set1)
      val modules2 = cache.getModuleNames(set2)

      val overlap = modules1.intersect(modules2)
      if (overlap.size > 5) { // Only report significant overlaps
        val uniqueToSet1 = (modules1 - modules2).sorted().take(10).ifEmpty { null }
        val uniqueToSet2 = (modules2 - modules1).sorted().take(10).ifEmpty { null }
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

  gen.writeFieldName("setOverlapAnalysis")
  gen.writeRawValue(kotlinxJson.encodeToString(setOverlapAnalysis))

  // xi:include duplicate detection
  val productFiles = products
    .mapNotNull { it.pluginXmlPath }
    .map { projectRoot.resolve(it) }
    .filter { it.exists() && it.isRegularFile() }

  val report = detectDuplicates(productFiles, projectRoot)
  gen.writeFieldName("xiIncludeDuplicates")
  gen.writeRawValue(kotlinxJson.encodeToString(report))
}
