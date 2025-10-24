// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.dev.ProductConfigurationRegistry
import org.jetbrains.intellij.build.dev.createProductProperties
import org.jetbrains.intellij.build.impl.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.findFileInModuleSources
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates an XML file for a module set.
 * Used to maintain backward compatibility with XML-based module set loading.
 *
 * @param moduleSet The module set to generate XML for
 * @param outputDir The directory where the XML file will be written
 * @param label Description label ("community" or "ultimate") for header generation
 * @return Result containing file status and statistics
 */
fun generateModuleSetXml(moduleSet: ModuleSet, outputDir: Path, label: String): ModuleSetFileResult {
  val fileName = "intellij.moduleSets.${moduleSet.name}.xml"
  val outputPath = outputDir.resolve(fileName)

  val xml = buildModuleSetXml(moduleSet, label)

  // determine change status
  val status = if (Files.exists(outputPath)) {
    val existingContent = Files.readString(outputPath)
    if (existingContent == xml) FileChangeStatus.UNCHANGED else FileChangeStatus.MODIFIED
  }
  else {
    FileChangeStatus.CREATED
  }

  // Only write if changed
  if (status != FileChangeStatus.UNCHANGED) {
    Files.writeString(outputPath, xml)
  }

  // Count direct modules (excluding nested)
  val nestedModuleNames = moduleSet.nestedSets.flatMap { it.modules.map { m -> m.name } }.toSet()
  val directModuleCount = moduleSet.modules.count { it.name !in nestedModuleNames }

  return ModuleSetFileResult(fileName, status, directModuleCount)
}

/**
 * Represents a single content block in a product plugin.xml.
 * Each block corresponds to a module set or additional modules section.
 */
data class ContentBlock(
  /** Source identifier for the block (e.g., "essential", "vcs", "additional") */
  val source: String,
  /** List of modules with their effective loading modes */
  val modules: List<ModuleWithLoading>,
)

/**
 * A module with its effective loading mode after applying overrides and exclusions.
 */
data class ModuleWithLoading(
  /** Module name */
  val name: String,
  /** Effective loading mode (null means default/no attribute) */
  val loading: ModuleLoadingRule?,
)

/**
 * Known product plugin.xml locations.
 * Maps product name (from dev-build.json) to relative path from project root.
 *
 * TODO: Make this more dynamic by discovering plugin.xml locations from module structure
 */
private val PRODUCT_PLUGIN_XML_PATHS = mapOf(
  "Gateway" to "remote-dev/gateway/resources/META-INF/plugin.xml",
  // Add more products here as they adopt programmatic content
)

/**
 * Generates complete product plugin.xml file from programmatic specification.
 *
 * @return Result containing file status and statistics
 */
fun generateProductXml(
  pluginXmlPath: Path,
  spec: ProductModulesContentSpec,
  productName: String,
  moduleOutputProvider: ModuleOutputProvider,
  projectRoot: Path,
): ProductFileResult {
  // Determine which generator to recommend based on plugin.xml file location
  // Community products are under community/ directory, Ultimate products are not
  val generatorCommand = (if (pluginXmlPath.toString().contains("/community/")) "CommunityModuleSets" else "UltimateModuleSets") + ".main()"

  // Build complete plugin.xml file and capture contentBlocks for statistics
  var contentBlocks: List<ContentBlock> = emptyList()
  val newContent = buildString {
    // Header comments
    append("  <!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
    append("  <!-- To regenerate, run 'Generate Product Layouts' or directly $generatorCommand -->\n")
    append("  <!-- Source: see getProductContentModules() in ${productName}Properties.kt -->\n")
    // Generated content (module alias, xi:includes, content blocks)
    contentBlocks = buildProductContentXml(spec = spec, moduleOutputProvider = moduleOutputProvider, sb = this)
  }

  // Compare with existing file if it exists
  val originalContent = if (Files.exists(pluginXmlPath)) Files.readString(pluginXmlPath) else null
  val status = if (originalContent == newContent) {
    FileChangeStatus.UNCHANGED
  }
  else {
    FileChangeStatus.MODIFIED
  }

  // Only write if changed
  if (status != FileChangeStatus.UNCHANGED) {
    Files.writeString(pluginXmlPath, newContent)
  }

  // Calculate statistics using the contentBlocks from generation
  val totalModules = contentBlocks.sumOf { it.modules.size }
  val relativePath = projectRoot.relativize(pluginXmlPath).toString()

  return ProductFileResult(
    productName = productName,
    relativePath = relativePath,
    status = status,
    includeCount = spec.deprecatedXmlIncludes.size,
    contentBlockCount = contentBlocks.size,
    totalModules = totalModules
  )
}

/**
 * Generates product XMLs for all products using programmatic content.
 * Discovers products from dev-build.json and generates complete plugin.xml files.
 *
 * @param projectRoot The project root path
 * @return Result containing generation statistics or null if dev-build.json doesn't exist
 */
suspend fun generateAllProductXmlFiles(projectRoot: Path): ProductGenerationResult? {
  val devBuildJsonPath = projectRoot.resolve("build/dev-build.json")
  if (Files.notExists(devBuildJsonPath)) {
    return null
  }

  try {
    val jsonContent = Files.readString(devBuildJsonPath)
    val productToConfiguration = Json.decodeFromString<ProductConfigurationRegistry>(jsonContent).products

    val project = JpsSerializationManager.getInstance().loadProject(projectRoot.toString(), mapOf("MAVEN_REPOSITORY" to JpsMavenSettings.getMavenRepositoryPath()), false)
    val moduleOutputProvider = ModuleOutputProvider.jps(project.modules)

    val productResults = mutableListOf<ProductFileResult>()
    for ((productName, pluginXmlRelativePath) in PRODUCT_PLUGIN_XML_PATHS) {
      val productProperties = createProductProperties(
        productConfiguration = productToConfiguration.get(productName) ?: continue,
        moduleOutputProvider = moduleOutputProvider,
        projectDir = projectRoot,
        platformPrefix = null
      )
      val spec = productProperties.getProductContentDescriptor()
      if (spec != null) {
        val pluginXmlPath = projectRoot.resolve(pluginXmlRelativePath)
        val result = generateProductXml(
          pluginXmlPath = pluginXmlPath,
          spec = spec,
          productName = productName,
          moduleOutputProvider = moduleOutputProvider,
          projectRoot = projectRoot
        )
        productResults.add(result)
      }
    }

    return ProductGenerationResult(productResults)
  }
  catch (e: Exception) {
    // If anything goes wrong, don't fail the build - just skip product generation
    System.err.println("Warning: Failed to process dev-build.json: ${e.message}")
    return null
  }
}

/**
 * Generates content blocks from a product modules specification.
 * This is the shared logic used by both static XML generation and runtime injection.
 *
 * @param spec The product modules specification
 * @return List of content blocks, each representing a `<content>` element
 */
private fun generateContentBlocks(spec: ProductModulesContentSpec): List<ContentBlock> {
  val result = mutableListOf<ContentBlock>()

  // Recursively collect all module sets (including nested ones)
  fun collectAllModuleSets(sets: List<ModuleSet>): List<ModuleSet> {
    val all = mutableListOf<ModuleSet>()
    for (set in sets) {
      all.add(set)
      all.addAll(collectAllModuleSets(set.nestedSets))
    }
    return all
  }

  val allModuleSets = collectAllModuleSets(spec.moduleSets)

  // Track which modules are in which sets for duplicate detection
  val moduleToSets = mutableMapOf<String, MutableList<String>>()

  // Process each module set
  for (moduleSet in allModuleSets) {
    // Get direct modules (excluding those in nested sets of this specific module set)
    val nestedModuleNames = moduleSet.nestedSets
      .flatMap { it.modules.map { m -> m.name } }
      .toSet()

    val directModules = moduleSet.modules
      .filter { it.name !in nestedModuleNames && it.name !in spec.excludedModules }

    // Track each module's set for duplicate detection
    for (module in directModules) {
      moduleToSets.computeIfAbsent(module.name) { mutableListOf() }.add(moduleSet.name)
    }

    // Generate content block with loading rules applied
    val modulesWithLoading = directModules.map { module ->
      val effectiveLoading = spec.moduleLoadingOverrides[module.name] ?: module.loading
      ModuleWithLoading(module.name, effectiveLoading)
    }

    if (modulesWithLoading.isNotEmpty()) {
      result.add(ContentBlock(moduleSet.name, modulesWithLoading))
    }
  }

  // Check for duplicates and FAIL if found
  val duplicates = moduleToSets.filter { it.value.size > 1 }
  if (duplicates.isNotEmpty()) {
    val errorMessage = buildString {
      appendLine("ERROR: Duplicate modules found across module sets:")
      for ((moduleName, sets) in duplicates.toSortedMap()) {
        appendLine("  - Module '$moduleName' appears in: ${sets.sorted().joinToString(", ")}")
      }
      appendLine()
      appendLine("Each module must belong to exactly one module set.")
      appendLine("Fix the module set definitions in CommunityModuleSets.kt or UltimateModuleSets.kt")
    }
    error(errorMessage)
  }

  // Add additional modules if any
  val additionalModulesWithLoading = spec.additionalModules
    .filter { it.name !in spec.excludedModules }
    .map { module ->
      val effectiveLoading = spec.moduleLoadingOverrides.get(module.name) ?: module.loading
      ModuleWithLoading(module.name, effectiveLoading)
    }

  if (additionalModulesWithLoading.isNotEmpty()) {
    result.add(ContentBlock("additional", additionalModulesWithLoading))
  }

  return result
}

/**
 * Builds XML content for programmatic product modules.
 * Generates module alias, xi:include directives (or inlined content), and `<content>` blocks for each module set.
 *
 * @param moduleOutputProvider Provider for module lookup and validation
 * @param inlineXmlIncludes If true, inline the actual XML content instead of generating xi:include directives (for runtime processing)
 * @return List of content blocks that were generated (useful for statistics)
 */
internal fun buildProductContentXml(
  spec: ProductModulesContentSpec,
  moduleOutputProvider: ModuleOutputProvider,
  sb: StringBuilder,
  inlineXmlIncludes: Boolean = false,
): List<ContentBlock> {
  // Opening tag with optional XInclude namespace
  if (spec.deprecatedXmlIncludes.isEmpty()) {
    sb.append("<idea-plugin>\n")
  }
  else {
    sb.append("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n")
  }

  // Generate module alias (if present)
  val aliasXml = buildModuleAliasXml(spec.productModuleAlias)
  if (aliasXml.isNotEmpty()) {
    sb.append(aliasXml)
    sb.append("\n")
  }

  // Generate xi:include directives or inline content
  if (spec.deprecatedXmlIncludes.isNotEmpty()) {
    generateXIncludes(spec = spec, moduleOutputProvider = moduleOutputProvider, inlineXmlIncludes = inlineXmlIncludes, sb = sb)
  }

  // Generate single content block with comments separating module sets
  val contentBlocks = generateContentBlocks(spec)
  if (contentBlocks.isNotEmpty()) {
    sb.append("  <content namespace=\"jetbrains\">\n")

    for ((index, block) in contentBlocks.withIndex()) {
      // Add comment to indicate source module set
      sb.append("    <!-- ${block.source} -->\n")

      for (moduleWithLoading in block.modules) {
        sb.append("    <module name=\"${moduleWithLoading.name}\"")

        if (moduleWithLoading.loading != null) {
          // convert enum to lowercase with hyphens (e.g., ON_DEMAND -> on-demand)
          sb.append(" loading=\"${moduleWithLoading.loading.name.lowercase().replace('_', '-')}\"")
        }

        sb.append("/>\n")
      }

      // Add blank line between sections for readability (except after last block)
      if (index < contentBlocks.size - 1) {
        sb.append("\n")
      }
    }

    sb.append("  </content>\n")
  }

  // Closing tag
  sb.append("</idea-plugin>\n")

  return contentBlocks
}

private fun generateXIncludes(
  spec: ProductModulesContentSpec,
  moduleOutputProvider: ModuleOutputProvider,
  inlineXmlIncludes: Boolean,
  sb: StringBuilder,
) {
  for (include in spec.deprecatedXmlIncludes) {
    // Find the module and file
    val module = moduleOutputProvider.findModule(include.moduleName)
                 ?: error("Module '${include.moduleName}' not found (referenced in xi:include for '${include.resourcePath}')")
    val file = findFileInModuleSources(module, include.resourcePath)
               ?: error("Resource '${include.resourcePath}' not found in module '${module.name}' (referenced in xi:include for '${include.resourcePath}')")

    if (inlineXmlIncludes) {
      // Inline the actual XML content
      for (element in JDOMUtil.load(file).children) {
        sb.append(JDOMUtil.write(element).prependIndent("  "))
        sb.append("\n")
        sb.append("\n")
      }
    }
    else {
      // Generate the xi:include with absolute path (resources are in /META-INF/... in jars)
      sb.append("  <xi:include href=\"/${include.resourcePath}\"/>")
      sb.append("\n")
    }
  }
  sb.append("\n")
}

