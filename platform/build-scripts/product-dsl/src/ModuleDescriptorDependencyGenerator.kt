// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.analysis.validateProductModuleSets
import org.jetbrains.intellij.build.productLayout.analysis.validateSelfContainedModuleSets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates module descriptor dependencies for all modules with includeDependencies=true.
 * 
 * **Validation Strategy:**
 * This function not only generates dependencies but also validates that all dependencies
 * (both direct and transitive) are reachable within the module set hierarchy.
 * 
 * **Why Transitive Validation Matters:**
 * Before this fix, only direct dependencies were validated. This caused runtime failures when:
 * - Module A depends on Module B (validated ✓)
 * - Module B depends on Module C (NOT validated ✗)
 * - Module C is missing from the module set
 * - At runtime: Module A loads → Module B loads → Module C fails!
 * 
 * **Example Bug Fixed:**
 * - `intellij.platform.kernel` (in `corePlatform()`) depends on `fleet.kernel`
 * - But `corePlatform()` doesn't include `fleet()` module set
 * - Modules in `corePlatform()` with `includeDependencies=true` would generate descriptors
 *   listing `intellij.platform.kernel` as a dependency
 * - At runtime, `intellij.platform.kernel` would fail to load because `fleet.kernel` is missing
 * - OLD: No validation error (only checked direct dependencies)
 * - NEW: Validation catches the missing `fleet.kernel` transitive dependency
 */
suspend fun generateModuleDescriptorDependencies(
  communityModuleSets: List<ModuleSet>,
  ultimateModuleSets: List<ModuleSet>,
  coreModuleSets: List<ModuleSet> = emptyList(),
  moduleOutputProvider: ModuleOutputProvider,
  productSpecs: List<Pair<String, ProductModulesContentSpec?>> = emptyList(),
): DependencyGenerationResult = coroutineScope {
  val allModuleSets = communityModuleSets + coreModuleSets + ultimateModuleSets
  val modulesToProcess = collectModulesWithIncludeDependencies(allModuleSets)
  if (modulesToProcess.isEmpty()) {
    return@coroutineScope DependencyGenerationResult(emptyList())
  }

  val cache = ModuleDescriptorCache(moduleOutputProvider)

  // Validate self-contained module sets in isolation
  // Module sets marked with selfContained=true must be resolvable without other sets
  validateSelfContainedModuleSets(allModuleSets, cache)
  
  // Tier 2: Validate product-level dependencies
  // This ensures all products can load without missing dependency errors
  validateProductModuleSets(
    allModuleSets = allModuleSets,
    productSpecs = productSpecs,
    descriptorCache = cache,
    allowUnresolvableProducts = setOf(
      // TODO: Fix these products' module dependencies
      "CLion",
      "DataSpell",
      "RustRover",
      "Rider",
      "WebStorm",
      "GoLand",
    )
  )

  // Write XML files in parallel
  val results = modulesToProcess.map { moduleName ->
    async {
      val info = cache.getOrAnalyze(moduleName) ?: return@async null
      val status = updateModuleDescriptor(info.descriptorPath, info.dependencies)
      DependencyFileResult(
        moduleName = moduleName,
        descriptorPath = info.descriptorPath,
        status = status,
        dependencyCount = info.dependencies.size
      )
    }
  }.awaitAll().filterNotNull()

  DependencyGenerationResult(results)
}

/**
 * Cache for module descriptor information to avoid redundant file system lookups.
 * Made public for access from analysis package.
 */
class ModuleDescriptorCache(private val moduleOutputProvider: ModuleOutputProvider) {
  data class DescriptorInfo(
    val descriptorPath: Path,
    val dependencies: List<String>,
  )

  // Wrapper to allow caching null results (ConcurrentHashMap doesn't support null values)
  private class CacheValue(@JvmField val info: DescriptorInfo?)

  private val cache = ConcurrentHashMap<String, CacheValue>()

  /**
   * Gets cached descriptor info or analyzes the module if not yet cached.
   * Caches both positive (has descriptor) and negative (no descriptor) results.
   * Thread-safe: ensures exactly one analysis per module using double-checked locking.
   */
  fun getOrAnalyze(moduleName: String): DescriptorInfo? {
    return (cache.get(moduleName) ?: synchronized(moduleName.intern()) {
      cache.getOrPut(moduleName) { CacheValue(analyzeModule(moduleName)) }
    }).info
  }

  /**
   * Analyzes a module to find its descriptor and production dependencies.
   */
  private fun analyzeModule(moduleName: String): DescriptorInfo? {
    val jpsModule = moduleOutputProvider.findRequiredModule(moduleName)
    val descriptorPath = findFileInModuleSources(
      module = jpsModule,
      relativePath = "$moduleName.xml",
      onlyProductionSources = true
    ) ?: return null

    // Skip modules with IJPL-210868 marker (not registered as content modules)
    if (shouldSkipDescriptor(descriptorPath)) {
      return null
    }

    val deps = mutableListOf<String>()
    for (dep in jpsModule.getProductionModuleDependencies(withTests = false)) {
      val depName = dep.moduleReference.moduleName
      if (hasDescriptor(depName)) {
        deps.add(depName)
      }
    }

    // Deduplicate MODULE DEPENDENCIES (not content modules!) before sorting.
    // Handles cases where the same dependency appears multiple times in JPS module graph.
    // Note: Content module duplicates are caught by validateProductModuleSets() during generation.
    return DescriptorInfo(descriptorPath, deps.distinct().sorted())
  }

  private fun shouldSkipDescriptor(descriptorPath: Path): Boolean {
    val content = Files.readString(descriptorPath)
    return content.contains("<!-- todo: register this as a content module (IJPL-210868)")
  }

  /**
   * Checks if a module has a descriptor XML file.
   */
  fun hasDescriptor(moduleName: String): Boolean = getOrAnalyze(moduleName) != null
}

/**
 * Collects all modules with includeDependencies=true from a list of module sets.
 */
private fun collectModulesWithIncludeDependencies(moduleSets: List<ModuleSet>): Set<String> {
  val result = mutableSetOf<String>()

  fun processModuleSet(moduleSet: ModuleSet) {
    // Add modules with includeDependencies=true
    for (module in moduleSet.modules) {
      if (module.includeDependencies) {
        result.add(module.name)
      }
    }

    // Recursively process nested sets
    for (nestedSet in moduleSet.nestedSets) {
      processModuleSet(nestedSet)
    }
  }

  for (moduleSet in moduleSets) {
    processModuleSet(moduleSet)
  }

  return result
}

/**
 * Updates module descriptor XML file with generated dependencies.
 * Replaces content between generation markers or entire dependencies section.
 * Returns the file change status.
 */
private fun updateModuleDescriptor(
  descriptorPath: Path,
  dependencies: List<String>,
): FileChangeStatus {
  if (Files.notExists(descriptorPath)) {
    return FileChangeStatus.UNCHANGED
  }

  val existingContent = Files.readString(descriptorPath)
  val newContent = replaceOrInsertDependencies(existingContent, dependencies)

  return if (newContent != existingContent) {
    Files.writeString(descriptorPath, newContent)
    // Check if this was a creation (no markers before) or modification
    if (existingContent.contains("<!-- editor-fold desc=\"Generated dependencies")) {
      FileChangeStatus.MODIFIED
    }
    else {
      FileChangeStatus.CREATED
    }
  }
  else {
    FileChangeStatus.UNCHANGED
  }
}

/**
 * Replaces dependencies section in XML, preserving other content.
 * Uses string-based parsing instead of regex for reliability.
 */
private fun replaceOrInsertDependencies(xmlContent: String, dependencies: List<String>): String {
  val generatedStart = "<!-- editor-fold desc=\"Generated dependencies - do not edit manually\" -->"
  val generatedEnd = "<!-- end editor-fold -->"

  // Legacy markers for backward compatibility
  val legacyStart = "<!-- Generated dependencies - do not edit manually -->"
  val legacyEnd = "<!-- End generated dependencies -->"

  // Generate new dependencies block
  val newDepsBlock = if (dependencies.isEmpty()) {
    ""
  }
  else {
    buildString {
      append("  $generatedStart\n")
      append("  <dependencies>\n")
      for (dep in dependencies) {
        append("    <module name=\"$dep\"/>\n")
      }
      append("  </dependencies>\n")
      append("  $generatedEnd\n")
    }
  }

  // Case 1: Check if current editor-fold markers exist
  val startIndex = xmlContent.indexOf(generatedStart)
  if (startIndex >= 0) {
    val endIndex = xmlContent.indexOf(generatedEnd, startIndex)
    if (endIndex >= 0) {
      val result = replaceBlockBetweenMarkers(xmlContent, startIndex, endIndex, generatedEnd.length, newDepsBlock)
      // Clean up any orphaned legacy markers
      return result.replace("  $legacyStart\n", "").replace("  $legacyEnd\n", "")
    }
  }

  // Case 2: Check if legacy markers exist and replace them
  val legacyStartIndex = xmlContent.indexOf(legacyStart)
  if (legacyStartIndex >= 0) {
    val legacyEndIndex = xmlContent.indexOf(legacyEnd, legacyStartIndex)
    if (legacyEndIndex >= 0) {
      return replaceBlockBetweenMarkers(xmlContent, legacyStartIndex, legacyEndIndex, legacyEnd.length, newDepsBlock)
    }
  }

  // Case 3: No markers found, try to replace entire <dependencies> section using string parsing
  val depsTagStart = xmlContent.indexOf("<dependencies>")
  if (depsTagStart >= 0) {
    val depsTagEnd = xmlContent.indexOf("</dependencies>", depsTagStart)
    if (depsTagEnd >= 0) {
      // Find line boundaries to replace entire block including indentation
      val lineStart = xmlContent.lastIndexOf('\n', depsTagStart - 1) + 1
      val lineEnd = xmlContent.indexOf('\n', depsTagEnd + "</dependencies>".length)
      val actualEnd = if (lineEnd >= 0) lineEnd + 1 else xmlContent.length
      return xmlContent.substring(0, lineStart) + newDepsBlock + xmlContent.substring(actualEnd)
    }
  }

  // Case 4: No dependencies section exists, insert after opening tag
  if (dependencies.isEmpty()) {
    return xmlContent
  }
  
  val insertionPoint = findInsertionPointAfterPluginTag(xmlContent)
  return if (insertionPoint > 0) {
    xmlContent.substring(0, insertionPoint) + newDepsBlock + xmlContent.substring(insertionPoint)
  }
  else {
    xmlContent
  }
}

/**
 * Replaces content between markers, preserving line boundaries.
 */
private fun replaceBlockBetweenMarkers(
  content: String,
  markerStart: Int,
  markerEnd: Int,
  markerEndLength: Int,
  newBlock: String
): String {
  // Find the start of the line (after previous newline) to preserve correct indentation
  val lineStartIndex = content.lastIndexOf('\n', markerStart - 1) + 1
  // Find end of line containing the end marker
  val endLineIndex = content.indexOf('\n', markerEnd + markerEndLength)
  val actualEndIndex = if (endLineIndex >= 0) endLineIndex + 1 else content.length
  return content.substring(0, lineStartIndex) + newBlock + content.substring(actualEndIndex)
}

/**
 * Finds the insertion point after the opening <idea-plugin> tag.
 * Returns -1 if the tag is not found.
 */
private fun findInsertionPointAfterPluginTag(xmlContent: String): Int {
  val pluginTagStart = xmlContent.indexOf("<idea-plugin")
  if (pluginTagStart < 0) return -1
  
  val pluginTagEnd = xmlContent.indexOf('>', pluginTagStart)
  if (pluginTagEnd < 0) return -1
  
  val newlineAfterTag = xmlContent.indexOf('\n', pluginTagEnd)
  return if (newlineAfterTag >= 0) newlineAfterTag + 1 else -1
}
