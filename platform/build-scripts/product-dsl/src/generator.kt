// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import org.jetbrains.intellij.build.ModuleOutputProvider

import org.jetbrains.intellij.build.productLayout.stats.ModuleSetFileResult
import org.jetbrains.intellij.build.productLayout.stats.ProductFileResult
import org.jetbrains.intellij.build.productLayout.stats.TestPluginFileResult
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.xml.appendContentBlock
import org.jetbrains.intellij.build.productLayout.xml.appendModuleLine
import org.jetbrains.intellij.build.productLayout.xml.appendModuleSetXml
import org.jetbrains.intellij.build.productLayout.xml.appendModuleSetsStrategyComment
import org.jetbrains.intellij.build.productLayout.xml.appendOpeningTag
import org.jetbrains.intellij.build.productLayout.xml.appendXmlHeader
import org.jetbrains.intellij.build.productLayout.xml.buildModuleAliasesXml
import org.jetbrains.intellij.build.productLayout.xml.generateXIncludes
import org.jetbrains.intellij.build.productLayout.xml.includesPlatformLangPlugin
import org.jetbrains.intellij.build.productLayout.xml.withEditorFold
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
internal fun generateModuleSetXml(moduleSet: ModuleSet, outputDir: Path, label: String, strategy: FileUpdateStrategy): ModuleSetFileResult {
  val fileName = "${MODULE_SET_PREFIX}${moduleSet.name}.xml"
  val outputPath = outputDir.resolve(fileName)
  val buildResult = buildModuleSetXml(moduleSet, label)
  val status = strategy.updateIfChanged(outputPath, buildResult.xml)
  return ModuleSetFileResult(fileName, status, buildResult.directModuleCount)
}

/**
 * Generates complete product plugin.xml file from programmatic specification.
 *
 * @param isUltimateBuild Whether this is an Ultimate build (vs. Community build)
 * @return Result containing file status and statistics
 */
internal fun generateProductXml(
  pluginXmlPath: Path,
  spec: ProductModulesContentSpec,
  productName: String,
  productPropertiesClass: String,
  outputProvider: ModuleOutputProvider,
  projectRoot: Path,
  isUltimateBuild: Boolean,
  strategy: FileUpdateStrategy,
): ProductFileResult {
  // Determine which generator to recommend based on plugin.xml file location
  // Community products are under community/ directory, Ultimate products are not
  val generatorCommand = (if (pluginXmlPath.toString().contains("/community/")) "CommunityModuleSets" else "UltimateGenerator") + GENERATOR_SUFFIX

  // Build complete plugin.xml file
  // inlineModuleSets = false means: use xi:include to reference module set XML files in product XML
  // Note: Module set XML files themselves always contain inlined module definitions
  val buildResult = buildProductContentXml(
    spec = spec,
    outputProvider = outputProvider,
    inlineXmlIncludes = false,
    inlineModuleSets = false,
    headerBuilder = { sb ->
      sb.appendXmlHeader(generatorCommand, productPropertiesClass)
    },
    metadataBuilder = { sb ->
      // Add id/name/vendor if NOT getting from xi:include (e.g., PlatformLangPlugin.xml)
      if (!spec.includesPlatformLangPlugin()) {
        sb.append("  <id>com.intellij</id>\n")
        sb.append("  <name>IDEA CORE</name>\n")
        if (spec.vendor != null) {
          sb.append("  <vendor>${spec.vendor}</vendor>\n")
        }
      }
    },
    isUltimateBuild = isUltimateBuild
  )

  val originalContent = Files.readString(pluginXmlPath)
  val status = strategy.writeIfChanged(pluginXmlPath, originalContent, buildResult.xml)

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
 * Generates a test plugin XML file from programmatic specification.
 * Test plugins have simpler structure than products - metadata, dependencies, and content modules.
 *
 * @param spec The test plugin specification
 * @param projectRoot The project root path
 * @param strategy File update strategy (for deferred writes)
 * @return Result containing file status and statistics
 */
internal fun generateTestPluginXml(
  spec: TestPluginSpec,
  productPropertiesClass: String,
  projectRoot: Path,
  moduleDependencies: List<ContentModuleName>,
  pluginDependencies: List<PluginId>,
  moduleDependencyChains: Map<ContentModuleName, List<ContentModuleName>> = emptyMap(),
  strategy: FileUpdateStrategy,
): TestPluginFileResult {
  val pluginXmlPath = projectRoot.resolve(spec.pluginXmlPath)
  val sortedContentSpec = sortTestPluginContentSpec(spec.spec)
  val sortedModuleDependencies = moduleDependencies.sortedBy { it.value }
  val sortedPluginDependencies = pluginDependencies.sortedBy { it.value }

  val moduleCommentProvider: (ContentModuleName, List<String>?) -> String? = { moduleName, moduleSetChain ->
    val dependencyChain = moduleDependencyChains.get(moduleName)
    if (dependencyChain != null) {
      "chain: " + dependencyChain.joinToString(" -> ") { it.value }
    }
    else if (moduleSetChain != null) {
      val chain = moduleSetChain.map { it.removePrefix(MODULE_SET_PREFIX) }
      "chain: moduleSet " + chain.joinToString(" -> ")
    }
    else {
      "chain: direct"
    }
  }

  // Reuse buildProductContentXml with test plugin metadata
  val buildResult = buildProductContentXml(
    spec = sortedContentSpec,
    outputProvider = null,
    inlineXmlIncludes = true,
    inlineModuleSets = true,
    headerBuilder = { sb ->
      sb.append("<!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
      sb.append("<!-- To regenerate, run 'Generate Product Layouts' or directly UltimateGenerator.main() -->\n")
      sb.append("<!-- Source: $productPropertiesClass.getProductContentDescriptor() -->\n")
    },
    metadataBuilder = { sb ->
      sb.append("  <id>${spec.pluginId.value}</id>\n")
      sb.append("  <name>${spec.name}</name>\n")
      sb.append("  <vendor>JetBrains</vendor>\n")
    },
    bodyBuilder = { sb ->
      if (sortedModuleDependencies.isNotEmpty() || sortedPluginDependencies.isNotEmpty()) {
        sb.append("\n")
        sb.append("  <dependencies>\n")
        for (plugin in sortedPluginDependencies) {
          sb.append("    <plugin id=\"${plugin.value}\"/>\n")
        }
        for (module in sortedModuleDependencies) {
          sb.append("    <module name=\"${module.value}\"/>\n")
        }
        sb.append("  </dependencies>\n")
      }
      sb.append("\n")
    },
    isUltimateBuild = true,
    moduleCommentProvider = moduleCommentProvider,
  )

  val originalContent = if (Files.exists(pluginXmlPath)) Files.readString(pluginXmlPath) else ""
  val status = strategy.writeIfChanged(pluginXmlPath, originalContent, buildResult.xml)
  val relativePath = projectRoot.relativize(pluginXmlPath).toString()

  return TestPluginFileResult(
    pluginId = spec.pluginId,
    relativePath = relativePath,
    status = status,
    moduleCount = buildResult.contentBlocks.sumOf { it.modules.size },
  )
}

private fun sortTestPluginContentSpec(spec: ProductModulesContentSpec): ProductModulesContentSpec {
  if (spec.moduleSets.isEmpty() && spec.additionalModules.isEmpty()) return spec

  val sortedModuleSets = spec.moduleSets
    .map { moduleSetWithOverrides ->
      moduleSetWithOverrides.copy(moduleSet = sortModuleSet(moduleSetWithOverrides.moduleSet))
    }
    .sortedBy { it.moduleSet.name }
  val sortedAdditionalModules = spec.additionalModules.sortedBy { it.name.value }

  return ProductModulesContentSpec(
    productModuleAliases = spec.productModuleAliases,
    vendor = spec.vendor,
    deprecatedXmlIncludes = spec.deprecatedXmlIncludes,
    moduleSets = sortedModuleSets,
    additionalModules = sortedAdditionalModules,
    bundledPlugins = spec.bundledPlugins,
    allowedMissingDependencies = spec.allowedMissingDependencies,
    compositionGraph = spec.compositionGraph,
    metadata = spec.metadata,
    testPlugins = spec.testPlugins,
  )
}

private fun sortModuleSet(moduleSet: ModuleSet): ModuleSet {
  val sortedModules = moduleSet.modules.sortedBy { it.name.value }
  val sortedNestedSets = moduleSet.nestedSets
    .map { sortModuleSet(it) }
    .sortedBy { it.name }
  return moduleSet.copy(modules = sortedModules, nestedSets = sortedNestedSets)
}

/**
 * Builds XML content for programmatic product/test plugin modules.
 * Generates module alias, xi:include directives (or inlined content), and `<content>` blocks for each module set.
 *
 * Used for both product plugin.xml and test plugin.xml generation. The opening `<idea-plugin>` tag
 * is handled automatically based on inlineXmlIncludes/inlineModuleSets. The [metadataBuilder] lambda
 * provides the content-specific parts: comments, id/name/vendor.
 *
 * @param spec The product modules specification
 * @param outputProvider Provider for module output paths (null for test plugins that don't need xi:include resolution)
 * @param inlineXmlIncludes Whether to inline legacy XML includes (vs. using xi:include)
 * @param inlineModuleSets Controls how module sets are rendered:
 *                         - false (default): Use xi:include directives to reference module set XML files
 *                         - true: Inline all module set content directly into XML
 * @param metadataBuilder Lambda that generates the metadata after opening tag (comments, id/name/vendor)
 * @param isUltimateBuild Whether this is an Ultimate build
 * @return Build result containing generated XML and metadata
 */
fun buildProductContentXml(
  spec: ProductModulesContentSpec,
  outputProvider: ModuleOutputProvider?,
  inlineXmlIncludes: Boolean,
  inlineModuleSets: Boolean,
  headerBuilder: ((StringBuilder) -> Unit)? = null,
  metadataBuilder: (StringBuilder) -> Unit,
  isUltimateBuild: Boolean,
  bodyBuilder: ((StringBuilder) -> Unit)? = null,
  moduleCommentProvider: ((ContentModuleName, List<String>?) -> String?)? = null,
): ProductContentBuildResult {
  // Build content blocks, chain mapping, and collect module set aliases in single traversal
  val buildData = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = inlineModuleSets)
  val contentBlocks = buildData.contentBlocks
  val moduleToSetChainMapping = buildData.moduleToSetChainMapping
  val moduleSetAliases = buildData.aliasToSource
  val moduleToIncludeDependenciesMapping = buildData.moduleToIncludeDependencies

  val xml = buildString {
    // Header comments go BEFORE the opening tag (outside <idea-plugin>)
    headerBuilder?.invoke(this)
    appendOpeningTag(spec, inlineXmlIncludes, inlineModuleSets)
    // Metadata (id/name/vendor) goes AFTER the opening tag (inside <idea-plugin>)
    metadataBuilder(this)
    bodyBuilder?.invoke(this)

    // Collect and validate product-level aliases, checking for conflicts with module set aliases
    val validatedAliases = collectAndValidateAliases(spec, moduleSetAliases)
    val aliasXml = buildModuleAliasesXml(validatedAliases)
    if (aliasXml.isNotEmpty()) {
      append(aliasXml)
      append("\n")
    }

    // Generate xi:include directives or inline content
    if (outputProvider != null && spec.deprecatedXmlIncludes.isNotEmpty()) {
      generateXIncludes(spec = spec, outputProvider = outputProvider, inlineXmlIncludes = inlineXmlIncludes, sb = this, isUltimateBuild = isUltimateBuild)
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
              val comment = moduleCommentProvider?.invoke(module.name, moduleToSetChainMapping[module.name])
              appendModuleLine(module, "    ", comment)
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
      appendContentBlock(
        blockSource = additionalBlock.source,
        modules = additionalBlock.modules,
        commentProvider = if (moduleCommentProvider == null) null else { module ->
          moduleCommentProvider(module.name, moduleToSetChainMapping[module.name])
        },
      )
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
