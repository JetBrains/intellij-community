// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import org.jetbrains.intellij.build.ModuleOutputProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Main entry point for generating XML files for both module sets and products.
 * This file orchestrates the generation process by calling specialized components for:
 * - Building content blocks (ContentBlockBuilder.kt)
 * - Validating specifications (ValidationUtils.kt)
 * - Rendering XML content (ModuleSetXmlRenderer.kt, ProductXmlRenderer.kt)
 */

/**
 * Generates an XML file for a module set.
 * Used to maintain backward compatibility with XML-based module set loading.
 * 
 * Module set XML files ALWAYS contain inlined module definitions - all direct modules
 * and nested module sets are expanded into `<module>` elements. The `inlineModuleSets`
 * parameter (used in product XML generation) only affects whether PRODUCT XMLs reference
 * these files via xi:include or inline them directly.
 *
 * @param moduleSet The module set to generate XML for
 * @param outputDir The directory where the XML file will be written
 * @param label Description label ("community" or "ultimate") for header generation
 * @return Result containing file status and statistics
 */
fun generateModuleSetXml(moduleSet: ModuleSet, outputDir: Path, label: String): ModuleSetFileResult {
  val fileName = "${MODULE_SET_PREFIX}${moduleSet.name}.xml"
  val outputPath = outputDir.resolve(fileName)

  val buildResult = buildModuleSetXml(moduleSet, label)

  // determine change status
  val status = when {
    !Files.exists(outputPath) -> FileChangeStatus.CREATED
    Files.readString(outputPath) == buildResult.xml -> FileChangeStatus.UNCHANGED
    else -> FileChangeStatus.MODIFIED
  }

  // Only write if changed
  if (status != FileChangeStatus.UNCHANGED) {
    Files.writeString(outputPath, buildResult.xml)
  }

  return ModuleSetFileResult(fileName, status, buildResult.directModuleCount)
}

/**
 * Generates complete product plugin.xml file from programmatic specification.
 *
 * @param isUltimateBuild Whether this is an Ultimate build (vs. Community build)
 * @return Result containing file status and statistics
 */
fun generateProductXml(
  pluginXmlPath: Path,
  spec: ProductModulesContentSpec,
  productName: String,
  productPropertiesClass: String,
  moduleOutputProvider: ModuleOutputProvider,
  projectRoot: Path,
  isUltimateBuild: Boolean,
): ProductFileResult {
  // Determine which generator to recommend based on plugin.xml file location
  // Community products are under community/ directory, Ultimate products are not
  val generatorCommand = (if (pluginXmlPath.toString().contains("/community/")) "CommunityModuleSets" else "UltimateModuleSets") + GENERATOR_SUFFIX

  // Build complete plugin.xml file
  // inlineModuleSets = false means: use xi:include to reference module set XML files in product XML
  // Note: Module set XML files themselves always contain inlined module definitions
  val buildResult = buildProductContentXml(
    spec = spec,
    moduleOutputProvider = moduleOutputProvider,
    inlineXmlIncludes = false,
    inlineModuleSets = false,
    productPropertiesClass = productPropertiesClass,
    generatorCommand = generatorCommand,
    isUltimateBuild = isUltimateBuild
  )

  // Compare with existing file if it exists
  val originalContent = Files.readString(pluginXmlPath)
  val status = if (originalContent == buildResult.xml) {
    FileChangeStatus.UNCHANGED
  }
  else {
    FileChangeStatus.MODIFIED
  }

  // Only write if changed
  if (status != FileChangeStatus.UNCHANGED) {
    Files.writeString(pluginXmlPath, buildResult.xml)
  }

  // Calculate statistics using the contentBlocks from generation
  val totalModules = buildResult.contentBlocks.sumOf { it.modules.size }
  val relativePath = projectRoot.relativize(pluginXmlPath).toString()

  return ProductFileResult(
    productName = productName,
    relativePath = relativePath,
    status = status,
    includeCount = spec.deprecatedXmlIncludes.size,
    contentBlockCount = buildResult.contentBlocks.size,
    totalModules = totalModules
  )
}

/**
 * Builds XML content for programmatic product modules.
 * Generates module alias, xi:include directives (or inlined content), and `<content>` blocks for each module set.
 * 
 * @param spec The product modules specification
 * @param moduleOutputProvider Provider for module output paths
 * @param inlineXmlIncludes Whether to inline legacy XML includes (vs. using xi:include)
 * @param inlineModuleSets Controls how module sets are rendered in PRODUCT XML files only.
 *                         - false (default): Use xi:include directives to reference module set XML files
 *                         - true: Inline all module set content directly into product XML
 *                         NOTE: Module set XML files (intellij.moduleSets.*.xml) always contain
 *                         inlined modules regardless of this flag. This parameter only affects
 *                         whether product XMLs reference those files via xi:include or inline them.
 * @param productPropertiesClass The product properties class name for header comment
 * @param generatorCommand The generator command name for header comment
 * @param isUltimateBuild Whether this is an Ultimate build
 * @return Build result containing generated XML and metadata
 */
fun buildProductContentXml(
  spec: ProductModulesContentSpec,
  moduleOutputProvider: ModuleOutputProvider,
  inlineXmlIncludes: Boolean,
  inlineModuleSets: Boolean,
  productPropertiesClass: String,
  generatorCommand: String,
  isUltimateBuild: Boolean,
): ProductContentBuildResult {
  // Build content blocks, chain mapping, and collect module set aliases in single traversal
  val buildData = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = inlineModuleSets)
  val contentBlocks = buildData.contentBlocks
  val moduleToSetChainMapping = buildData.moduleToSetChainMapping
  val moduleSetAliases = buildData.aliasToSource
  val moduleToIncludeDependenciesMapping = buildData.moduleToIncludeDependencies

  val xml = buildString {
    appendXmlHeader(generatorCommand, productPropertiesClass)
    appendOpeningTag(spec, inlineXmlIncludes, inlineModuleSets)

    // Collect and validate product-level aliases, checking for conflicts with module set aliases
    val validatedAliases = collectAndValidateAliases(spec, moduleSetAliases)
    val aliasXml = buildModuleAliasesXml(validatedAliases)
    if (aliasXml.isNotEmpty()) {
      append(aliasXml)
      append("\n")
    }

    // Generate xi:include directives or inline content
    if (spec.deprecatedXmlIncludes.isNotEmpty()) {
      generateXIncludes(spec = spec, moduleOutputProvider = moduleOutputProvider, inlineXmlIncludes = inlineXmlIncludes, sb = this, isUltimateBuild = isUltimateBuild)
    }

    // Generate module sets as xi:includes or inline content blocks
    if (spec.moduleSets.isNotEmpty()) {
      if (inlineModuleSets) {
        // Generate single content block with all module sets inlined
        append("  <content namespace=\"$JETBRAINS_NAMESPACE\">\n")
        for ((index, block) in contentBlocks.withIndex()) {
          if (block.source == ADDITIONAL_MODULES_BLOCK) continue // Skip additional modules, handle separately
          withEditorFold(this, "    ", block.source) {
            for (module in block.modules) {
              appendModuleLine(module, "    ")
            }
          }
          // Add blank line between sections for readability (except after last block)
          if (index < contentBlocks.size - 1) {
            append("\n")
          }
        }
        append("  </content>\n")
      }
      else {
        // Build set of module set names that are referenced at top-level WITH overrides
        // These cannot be brought in via `xi:include` from parent sets (would lose overrides)
        val overriddenModuleSetNames = spec.moduleSets
          .filter { it.hasOverrides }
          .map { ModuleSetName(it.moduleSet.name) }
          .toSet()

        appendModuleSetsStrategyComment(spec, overriddenModuleSetNames)

        // Generate content for each top-level module set
        for (moduleSetWithOverrides in spec.moduleSets) {
          appendModuleSetXml(
            moduleSetWithOverrides.moduleSet,
            moduleSetWithOverrides.loadingOverrides,
            contentBlocks,
            overriddenModuleSetNames
          )
        }
      }
    }

    // Handle additional modules separately (they don't have XML files, inline them)
    val additionalBlock = contentBlocks.firstOrNull { it.source == ADDITIONAL_MODULES_BLOCK }
    if (additionalBlock != null) {
      appendContentBlock(additionalBlock.source, additionalBlock.modules)
    }

    // Closing tag
    append("</idea-plugin>\n")
  }

  return ProductContentBuildResult(
    xml = xml,
    contentBlocks = contentBlocks,
    moduleToSetChainMapping = moduleToSetChainMapping,
    moduleToIncludeDependenciesMapping = moduleToIncludeDependenciesMapping,
  )
}
