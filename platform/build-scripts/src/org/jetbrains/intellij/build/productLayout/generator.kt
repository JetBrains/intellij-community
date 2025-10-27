// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import com.intellij.util.lang.ImmutableZipFile
import kotlinx.serialization.json.Json
import org.jdom.Element
import org.jetbrains.intellij.build.dev.createProductProperties
import org.jetbrains.intellij.build.impl.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.findFileInModuleSources
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
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

  val buildResult = buildModuleSetXml(moduleSet, label)

  // determine change status
  val status = if (Files.exists(outputPath)) {
    val existingContent = Files.readString(outputPath)
    if (existingContent == buildResult.xml) FileChangeStatus.UNCHANGED else FileChangeStatus.MODIFIED
  }
  else {
    FileChangeStatus.CREATED
  }

  // Only write if changed
  if (status != FileChangeStatus.UNCHANGED) {
    Files.writeString(outputPath, buildResult.xml)
  }

  return ModuleSetFileResult(fileName, status, buildResult.directModuleCount)
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
 * Result of building product content XML.
 * Contains the generated XML string, content blocks, and module-to-set chain mapping.
 */
data class ProductContentBuildResult(
  /** Generated XML content as string */
  val xml: String,
  /** List of content blocks generated from the spec */
  val contentBlocks: List<ContentBlock>,
  /** Mapping from module name to its module set chain as list (e.g., ["parent", "child"]) */
  val moduleToSetChainMapping: Map<String, List<String>>,
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
  productPropertiesClass: String,
  moduleOutputProvider: ModuleOutputProvider,
  projectRoot: Path,
): ProductFileResult {
  // Determine which generator to recommend based on plugin.xml file location
  // Community products are under community/ directory, Ultimate products are not
  val generatorCommand = (if (pluginXmlPath.toString().contains("/community/")) "CommunityModuleSets" else "UltimateModuleSets") + ".main()"

  // Build complete plugin.xml file
  val buildResult = buildProductContentXml(
    spec = spec,
    moduleOutputProvider = moduleOutputProvider,
    inlineXmlIncludes = true,
    productPropertiesClass = productPropertiesClass,
    generatorCommand = generatorCommand
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
 * Generates product XMLs for all products using programmatic content.
 * Discovers products from dev-build.json and generates complete plugin.xml files.
 *
 * @param projectRoot The project root path
 * @return Result containing generation statistics or null if dev-build.json doesn't exist
 */
suspend fun generateAllProductXmlFiles(projectRoot: Path): ProductGenerationResult {
  val jsonContent = Files.readString(projectRoot.resolve(PRODUCT_REGISTRY_PATH))
  val productToConfiguration = Json.decodeFromString<ProductConfigurationRegistry>(jsonContent).products

  val project = JpsSerializationManager.getInstance().loadProject(projectRoot.toString(), mapOf("MAVEN_REPOSITORY" to JpsMavenSettings.getMavenRepositoryPath()), false)
  val moduleOutputProvider = ModuleOutputProvider.jps(project.modules)

  val productResults = mutableListOf<ProductFileResult>()
  for ((productName, productConfig) in productToConfiguration) {
    // Skip products without pluginXmlPath configured
    val pluginXmlRelativePath = productConfig.pluginXmlPath ?: continue

    val productProperties = createProductProperties(
      productConfiguration = productConfig,
      moduleOutputProvider = moduleOutputProvider,
      projectDir = projectRoot,
      platformPrefix = null
    )
    val spec = productProperties.getProductContentDescriptor() ?: continue

    val pluginXmlPath = projectRoot.resolve(pluginXmlRelativePath)
    val result = generateProductXml(
      pluginXmlPath = pluginXmlPath,
      spec = spec,
      productName = productName,
      moduleOutputProvider = moduleOutputProvider,
      productPropertiesClass = productProperties::class.java.name,
      projectRoot = projectRoot,
    )
    productResults.add(result)
  }

  return ProductGenerationResult(productResults)
}


/**
 * Generates content blocks and module-to-set chain mapping in a single hierarchical traversal.
 * This optimized version eliminates redundant tree walking by computing both results simultaneously.
 *
 * @param spec The product modules specification
 * @return Pair of (content blocks, module-to-set chain mapping)
 */
private fun generateContentBlocksWithChainMapping(
  spec: ProductModulesContentSpec
): Pair<List<ContentBlock>, Map<String, List<String>>> {
  val contentBlocks = mutableListOf<ContentBlock>()
  val moduleToChain = mutableMapOf<String, List<String>>()
  val moduleToSets = mutableMapOf<String, MutableList<String>>()
  val processedSets = HashSet<String>()

  fun traverse(moduleSet: ModuleSet, chain: List<String>) {
    val setName = "intellij.moduleSets.${moduleSet.name}"
    val currentChain = chain + setName

    // Skip if already processed
    if (!processedSets.add(setName)) {
      return
    }

    // Get direct modules for this set
    val directModules = getDirectModules(moduleSet, spec.excludedModules)

    // Build content block and track chains/duplicates in single pass
    val modulesWithLoading = mutableListOf<ModuleWithLoading>()
    for (module in directModules) {
      // Track for duplicate detection
      moduleToSets.computeIfAbsent(module.name) { mutableListOf() }.add(moduleSet.name)
      // Track chain
      moduleToChain[module.name] = currentChain
      // Build loading info
      val effectiveLoading = spec.moduleLoadingOverrides[module.name] ?: module.loading
      modulesWithLoading.add(ModuleWithLoading(module.name, effectiveLoading))
    }

    if (modulesWithLoading.isNotEmpty()) {
      contentBlocks.add(ContentBlock(moduleSet.name, modulesWithLoading))
    }

    // Recursively process nested sets
    for (nestedSet in moduleSet.nestedSets) {
      traverse(nestedSet, currentChain)
    }
  }

  // Process all top-level module sets
  for (moduleSet in spec.moduleSets) {
    traverse(moduleSet, emptyList())
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
  val additionalModulesWithLoading = mutableListOf<ModuleWithLoading>()
  for (module in spec.additionalModules) {
    if (module.name !in spec.excludedModules) {
      val effectiveLoading = spec.moduleLoadingOverrides[module.name] ?: module.loading
      additionalModulesWithLoading.add(ModuleWithLoading(module.name, effectiveLoading))
    }
  }

  if (additionalModulesWithLoading.isNotEmpty()) {
    contentBlocks.add(ContentBlock("additional", additionalModulesWithLoading))
  }

  return Pair(contentBlocks, moduleToChain)
}

/**
 * Builds XML content for programmatic product modules.
 * Generates module alias, xi:include directives (or inlined content), and `<content>` blocks for each module set.
 */
internal fun buildProductContentXml(
  spec: ProductModulesContentSpec,
  moduleOutputProvider: ModuleOutputProvider,
  inlineXmlIncludes: Boolean,
  productPropertiesClass: String,
  generatorCommand: String,
): ProductContentBuildResult {
  // Generate content blocks and module-to-set chain mapping in single pass
  val (contentBlocks, moduleToSetChainMapping) = generateContentBlocksWithChainMapping(spec)

  val xml = buildString {
    // Header comments
    append("  <!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
    append("  <!-- To regenerate, run 'Generate Product Layouts' or directly $generatorCommand -->\n")
    append("  <!-- Source: $productPropertiesClass -->\n")

    // Opening tag with optional XInclude namespace
    if (spec.deprecatedXmlIncludes.isEmpty()) {
      append("<idea-plugin>\n")
    }
    else {
      append("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n")
    }

    // Collect aliases from all module sets (including nested ones)
    val allAliases = mutableListOf<String>()
    allAliases.addAll(spec.productModuleAliases)
    visitAllModuleSets(spec.moduleSets) { moduleSet ->
      if (moduleSet.alias != null) {
        allAliases.add(moduleSet.alias)
      }
    }

    val aliasXml = buildModuleAliasesXml(allAliases)
    if (aliasXml.isNotEmpty()) {
      append(aliasXml)
      append("\n")
    }

    // Generate xi:include directives or inline content
    if (spec.deprecatedXmlIncludes.isNotEmpty()) {
      generateXIncludes(spec = spec, moduleOutputProvider = moduleOutputProvider, inlineXmlIncludes = inlineXmlIncludes, sb = this)
    }

    // Generate single content block with comments separating module sets
    if (contentBlocks.isNotEmpty()) {
      append("  <content namespace=\"jetbrains\">\n")

      for ((index, block) in contentBlocks.withIndex()) {
        withEditorFold(this, "    ", block.source) {
          for (moduleWithLoading in block.modules) {
            append("    <module name=\"${moduleWithLoading.name}\"")

            if (moduleWithLoading.loading != null) {
              // convert enum to lowercase with hyphens (e.g., ON_DEMAND -> on-demand)
              append(" loading=\"${moduleWithLoading.loading.name.lowercase().replace('_', '-')}\"")
            }

            append("/>\n")
          }
        }

        // Add blank line between sections for readability (except after last block)
        if (index < contentBlocks.size - 1) {
          append("\n")
        }
      }

      append("  </content>\n")
    }

    // Closing tag
    append("</idea-plugin>\n")
  }

  return ProductContentBuildResult(xml = xml, contentBlocks = contentBlocks, moduleToSetChainMapping = moduleToSetChainMapping)
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
    val data = findFileInModuleSources(module, include.resourcePath)?.let { JDOMUtil.load(it) }
               ?: findFileInModuleLibraries(module, include.resourcePath)
               ?: error("Resource '${include.resourcePath}' not found in module '${module.name}' sources or libraries (referenced in xi:include)")

    if (inlineXmlIncludes) {
      withEditorFold(sb, "  ", "Inlined from ${include.moduleName}/${include.resourcePath}") {
        // Inline the actual XML content
        for (element in data.children) {
          sb.append(JDOMUtil.write(element).prependIndent("  "))
          sb.append("\n")
        }
      }
      sb.append("\n")
    }
    else {
      // Generate the xi:include with absolute path (resources are in /META-INF/... in jars)
      sb.append("  <xi:include href=\"/${include.resourcePath}\"/>")
      sb.append("\n")
    }
  }
}

/**
 * Searches for a file in the module's library dependencies (JARs).
 * This is used as a fallback when a resource is not found in module sources.
 *
 * @param module The module whose library dependencies to search
 * @param relativePath The relative path to the resource (e.g., "META-INF/plugin.xml")
 * @return Path to the resource inside a JAR, or null if not found
 */
private fun findFileInModuleLibraries(module: JpsModule, relativePath: String): Element? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency is JpsLibraryDependency) {
      val library = dependency.library ?: continue
      for (jarPath in library.getPaths(JpsOrderRootType.COMPILED)) {
        ImmutableZipFile.load(jarPath).use { zipFile ->
          zipFile.getData(relativePath)?.let { return JDOMUtil.load(it) }
        }
      }
    }
  }
  return null
}
