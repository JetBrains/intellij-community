// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.dev.createProductProperties
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.impl.ModuleOutputProvider
import org.jetbrains.intellij.build.isModuleNameLikeFilename
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
  val generatorCommand = (if (pluginXmlPath.toString().contains("/community/")) "CommunityModuleSets" else "UltimateModuleSets") + ".main()"

  // Build complete plugin.xml file
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

  // Detect if this is an Ultimate build (projectRoot != communityRoot)
  val isUltimateBuild = projectRoot != BuildPaths.COMMUNITY_ROOT.communityRoot

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
      isUltimateBuild = isUltimateBuild,
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
  spec: ProductModulesContentSpec,
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
 * Appends a single module XML element with optional loading attribute.
 */
private fun StringBuilder.appendModuleLine(moduleWithLoading: ModuleWithLoading, indent: String = "    ") {
  append("$indent<module name=\"${moduleWithLoading.name}\"")
  if (moduleWithLoading.loading != null) {
    // convert enum to lowercase with hyphens (e.g., ON_DEMAND -> on-demand)
    append(" loading=\"${moduleWithLoading.loading.name.lowercase().replace('_', '-')}\"")
  }
  append("/>\n")
}

/**
 * Appends a content block with modules wrapped in editor fold.
 */
private fun StringBuilder.appendContentBlock(
  blockSource: String,
  modules: List<ModuleWithLoading>,
  indent: String = "  ",
) {
  append("$indent<content namespace=\"jetbrains\">\n")
  withEditorFold(this, "$indent  ", blockSource) {
    for (module in modules) {
      appendModuleLine(module, "$indent  ")
    }
  }
  append("$indent</content>\n")
}

/**
 * Collects and validates module aliases from the spec.
 * Checks for duplicates and fails if any are found.
 *
 * @param spec The product modules specification
 * @param inlineModuleSets Whether module sets are being inlined
 * @return List of validated unique aliases
 */
private fun collectAndValidateAliases(spec: ProductModulesContentSpec, inlineModuleSets: Boolean): List<String> {
  val aliasToSource = HashMap<String, String>()

  // Collect product-level aliases
  for (alias in spec.productModuleAliases) {
    val existing = aliasToSource.put(alias, "product level")
    if (existing != null) {
      error("Duplicate alias '$alias' found at product level (already defined in: $existing)")
    }
  }

  // When inlining module sets, also collect their aliases
  if (inlineModuleSets) {
    visitAllModuleSets(spec.moduleSets) { moduleSet ->
      if (moduleSet.alias != null) {
        val existing = aliasToSource.put(moduleSet.alias, "module set '${moduleSet.name}'")
        if (existing != null) {
          error("Duplicate alias '${moduleSet.alias}' in module set '${moduleSet.name}' (already defined in: $existing)")
        }
      }
    }
  }

  return aliasToSource.keys.sorted()
}

/**
 * Builds XML content for programmatic product modules.
 * Generates module alias, xi:include directives (or inlined content), and `<content>` blocks for each module set.
 */
internal fun buildProductContentXml(
  spec: ProductModulesContentSpec,
  moduleOutputProvider: ModuleOutputProvider,
  inlineXmlIncludes: Boolean,
  inlineModuleSets: Boolean,
  productPropertiesClass: String,
  generatorCommand: String,
  isUltimateBuild: Boolean,
): ProductContentBuildResult {
  // Generate content blocks and module-to-set chain mapping in single pass
  val (contentBlocks, moduleToSetChainMapping) = generateContentBlocksWithChainMapping(spec)

  val xml = buildString {
    // Header comments
    append("  <!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
    append("  <!-- To regenerate, run 'Generate Product Layouts' or directly $generatorCommand -->\n")
    append("  <!-- Source: $productPropertiesClass -->\n")

    // Opening tag with optional XInclude namespace
    val needsXiNamespace = (!inlineXmlIncludes && spec.deprecatedXmlIncludes.isNotEmpty()) ||
                           (!inlineModuleSets && spec.moduleSets.isNotEmpty())

    // Check if PlatformLangPlugin.xml is included - if NOT, we need explicit id/name tags
    // Products that include PlatformLangPlugin.xml inherit id/name from it
    // Products without it (like Git Client) need explicit child tags
    val includesPlatformLang = spec.deprecatedXmlIncludes.any {
      it.resourcePath == "META-INF/PlatformLangPlugin.xml" ||
      it.resourcePath == "META-INF/JavaIdePlugin.xml" ||
      it.resourcePath == "META-INF/pycharm-core.xml"
    }

    if (needsXiNamespace) {
      append("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n")
    }
    else {
      append("<idea-plugin>\n")
    }

    // Add id and name as child tags if PlatformLangPlugin.xml is not included
    if (!includesPlatformLang) {
      append("  <id>com.intellij</id>\n")
      append("  <name>IDEA CORE</name>\n")
    }

    // Collect and validate aliases in a single pass
    val validatedAliases = collectAndValidateAliases(spec, inlineModuleSets)
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
        append("  <content namespace=\"jetbrains\">\n")
        for ((index, block) in contentBlocks.withIndex()) {
          if (block.source == "additional") continue // Skip additional modules, handle separately
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
        // Generate xi:include directives for top-level module sets only (nested sets are resolved via parent includes)
        for (moduleSet in spec.moduleSets) {
          append("  <xi:include href=\"/META-INF/intellij.moduleSets.${moduleSet.name}.xml\"/>\n")
        }
      }
    }

    // Handle additional modules separately (they don't have XML files, inline them)
    val additionalBlock = contentBlocks.firstOrNull { it.source == "additional" }
    if (additionalBlock != null) {
      appendContentBlock(additionalBlock.source, additionalBlock.modules)
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
  isUltimateBuild: Boolean,
) {
  for (include in spec.deprecatedXmlIncludes) {
    // When inlining: skip ultimate-only xi-includes in Community builds
    if (inlineXmlIncludes && include.ultimateOnly && !isUltimateBuild) {
      continue
    }

    // Find the module and file
    val module = moduleOutputProvider.findModule(include.moduleName)
    val resourcePath = include.resourcePath
    if (module == null) {
      if (include.ultimateOnly) {
        error("Ultimate-only module '${include.moduleName}' not found in Ultimate build - this is a configuration error (referenced in xi:include for '$resourcePath')")
      }
      error("Module '${include.moduleName}' not found (referenced in xi:include for '$resourcePath')")
    }

    val data = findFileInModuleSources(module, resourcePath)?.let { JDOMUtil.load(it) }
               ?: findFileInModuleLibraryDependencies(module = module, relativePath = resourcePath)?.let { JDOMUtil.load(it) }
               ?: error("Resource '$resourcePath' not found in module '${module.name}' sources or libraries (referenced in xi:include)")

    if (inlineXmlIncludes) {
      withEditorFold(sb, "  ", "Inlined from ${include.moduleName}/$resourcePath") {
        // Inline the actual XML content
        for (element in data.children) {
          sb.append(JDOMUtil.write(element).prependIndent("  "))
          sb.append("\n")
        }
      }
      sb.append("\n")
    }
    else {
      // Generate xi:include with absolute path (resources are in /META-INF/... in jars)
      // Wrap ultimate-only xi-includes with xi:fallback for graceful handling in Community builds
      if (include.ultimateOnly) {
        sb.append("""  <xi:include href="${resourcePathToXIncludePath(resourcePath)}">""")
        sb.append("\n")
        sb.append("""    <xi:fallback/>""")
        sb.append("\n")
        sb.append("""  </xi:include>""")
        sb.append("\n")
      }
      else {
        sb.append("""  <xi:include href="${resourcePathToXIncludePath(resourcePath)}"/>""")
        sb.append("\n")
      }
    }
  }
}

private fun resourcePathToXIncludePath(resourcePath: String): String {
  return if (isModuleNameLikeFilename(resourcePath)) resourcePath else "/$resourcePath"
}