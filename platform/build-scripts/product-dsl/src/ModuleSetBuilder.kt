// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.discovery.discoverModuleSets
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetFileResult
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetGenerationResult
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a content module with optional loading attribute.
 *
 * @param name Module name
 * @param loading Optional loading mode (e.g., ModuleLoadingRule.EMBEDDED)
 */
@Serializable
data class ContentModule(
  @JvmField val name: String,
  @JvmField val loading: ModuleLoadingRuleValue? = null,
  @JvmField val includeDependencies: Boolean = false,
)

/**
 * Represents a named collection of content modules.
 * The name serves as metadata for debugging and XML generation (used in the 'source' attribute).
 *
 * @param name Identifier for the module set (e.g., "essential", "vcs", "ssh")
 * @param modules List of content modules in this set
 * @param nestedSets List of nested module sets (for xi:include generation)
 * @param alias Optional module alias for `<module value="..."/>` declaration (e.g., "com.intellij.modules.xml")
 * @param outputModule Optional module name whose resources directory should be used for generating this module set's XML
 * @param selfContained If true, this module set will be validated in isolation to ensure all dependencies are resolvable within the set itself. Use for module sets that are designed to be standalone (e.g., `core.platform`). Default: false.
 */
@Serializable
data class ModuleSet(
  @JvmField val name: String,
  @JvmField val modules: List<ContentModule>,
  @JvmField val nestedSets: List<ModuleSet> = emptyList(),
  @JvmField val alias: String? = null,
  @JvmField @kotlinx.serialization.Transient val outputModule: String? = null,
  @JvmField val selfContained: Boolean = false,
)

/**
 * DSL builder for creating ModuleSets with reduced boilerplate.
 */
@ProductDslMarker
class ModuleSetBuilder(private val defaultIncludeDependencies: Boolean = false) {
  private val modules = ArrayList<ContentModule>()
  private val nestedSets = ArrayList<ModuleSet>()

  /**
   * Add a single module.
   */
  fun module(name: String, loading: ModuleLoadingRuleValue? = null) {
    modules.add(ContentModule(name, loading))
  }

  /**
   * Add a single module with EMBEDDED loading.
   */
  fun embeddedModule(name: String) {
    modules.add(ContentModule(name, ModuleLoadingRuleValue.EMBEDDED, defaultIncludeDependencies))
  }

  /**
   * Include another ModuleSet.
   */
  fun moduleSet(set: ModuleSet) {
    nestedSets.add(set)
  }

  @PublishedApi
  internal fun build(): Pair<List<ContentModule>, List<ModuleSet>> = Pair(java.util.List.copyOf(modules), java.util.List.copyOf(nestedSets))
}

/**
 * Creates a ModuleSet using DSL syntax.
 *
 * Example:
 * ```
 * fun ssh() = moduleSet("ssh") {
 *   embeddedModule("intellij.platform.ssh.core")
 *   embeddedModule("intellij.platform.ssh")
 *   module("intellij.platform.ssh.ui")
 * }
 *
 * // With module alias:
 * fun xml() = moduleSet("xml", alias = "com.intellij.modules.xml") {
 *   embeddedModule("intellij.xml.dom")
 *   // ...
 * }
 *
 * // With custom output module:
 * fun corePlatform() = moduleSet("core.platform", outputModule = "intellij.platform.ide.core") {
 *   // ...
 * }
 *
 * // With selfContained flag:
 * fun corePlatform() = moduleSet("core.platform", selfContained = true, outputModule = "intellij.platform.ide.core") {
 *   // Must include all dependencies - validated in isolation
 * }
 *
 * // With includeDependencies default for all embedded modules:
 * fun corePlatform() = moduleSet("core.platform", includeDependencies = true) {
 *   embeddedModule("intellij.platform.util.ex")  // inherits includeDependencies=true
 *   embeddedModule("intellij.platform.core")     // inherits includeDependencies=true
 *   embeddedModule("some.module", includeDependencies = false)  // explicit override
 * }
 * ```
 */
inline fun moduleSet(
  name: String,
  alias: String? = null,
  outputModule: String? = null,
  selfContained: Boolean = false,
  includeDependencies: Boolean = false,
  block: ModuleSetBuilder.() -> Unit,
): ModuleSet {
  val (modules, nestedSets) = ModuleSetBuilder(defaultIncludeDependencies = includeDependencies).apply(block).build()
  return ModuleSet(name = name, modules = modules, nestedSets = nestedSets, alias = alias, outputModule = outputModule, selfContained = selfContained)
}

/**
 * Appends a single module XML element to the StringBuilder.
 */
private fun appendModuleXml(sb: StringBuilder, module: ContentModule) {
  sb.append("    <module name=\"${module.name}\"")
  if (module.loading == ModuleLoadingRuleValue.EMBEDDED) {
    sb.append(" loading=\"embedded\"")
  }
  sb.append("/>")
  sb.append("\n")
}

/**
 * Recursively appends modules from a module set, including nested sets.
 * Handles nested sets at any depth with breadcrumb trail showing full hierarchy.
 */
private fun appendModuleSetContent(sb: StringBuilder, moduleSet: ModuleSet, indent: String = "    ", breadcrumb: String = "") {
  // Get direct modules (not from nested sets)
  val directModules = moduleSet.modules

  // Recursively append nested sets first
  for (nestedSet in moduleSet.nestedSets) {
    // Build breadcrumb path
    val nestedBreadcrumb = if (breadcrumb.isEmpty()) {
      nestedSet.name
    }
    else {
      "$breadcrumb > ${nestedSet.name}"
    }

    withEditorFold(sb, indent, "nested: $nestedBreadcrumb") {
      appendModuleSetContent(sb = sb, moduleSet = nestedSet, indent = indent, breadcrumb = nestedBreadcrumb) // RECURSIVE CALL with breadcrumb
    }
    sb.append("\n")
  }

  // Then append direct modules
  if (directModules.isNotEmpty()) {
    if (moduleSet.nestedSets.isNotEmpty()) {
      withEditorFold(sb, indent, "direct modules") {
        for (module in directModules) {
          appendModuleXml(sb, module)
        }
      }
    }
    else {
      for (module in directModules) {
        appendModuleXml(sb, module)
      }
    }
  }
}

/**
 * Builds the XML content for a module set.
 *
 * @param moduleSet The module set to build XML for
 * @param label Description label ("community" or "ultimate") for header generation
 * @return ModuleSetBuildResult containing XML and direct module count
 */
internal fun buildModuleSetXml(moduleSet: ModuleSet, label: String): ModuleSetBuildResult {
  val xml = buildString {
    // Add generated file header
    val mainClass = if (label == "community") "CommunityModuleSets" else "UltimateModuleSets"
    append("<!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
    append("<!-- To regenerate, run: `Generate Product Layouts` or `bazel run //platform/buildScripts:plugin-model-tool` -->\n")
    append("<!-- Source: see moduleSet(\"${moduleSet.name}\") function in ${mainClass}.kt -->\n")
    append("<!-- Note: Files are kept under VCS to support running products without dev mode (deprecated) -->\n")

    // Opening tag (no xmlns:xi needed since we inline nested sets)
    append("<idea-plugin>")
    append("\n")

    // Output all module aliases (recursively collected from this set and nested sets)
    val allAliases = collectAllAliases(moduleSet)
    if (allAliases.isNotEmpty()) {
      append(buildModuleAliasesXml(allAliases))
      append("\n")
    }

    // Generate content blocks with source-file attributes for tracking
    val hasAnyModules = moduleSet.nestedSets.isNotEmpty() || moduleSet.modules.isNotEmpty()
    if (hasAnyModules) {
      append("  <content namespace=\"jetbrains\">")
      append("\n")
      appendModuleSetContent(this, moduleSet)
      append("  </content>")
      append("\n")
    }

    // Closing tag - NO trailing newline
    append("</idea-plugin>")
  }

  return ModuleSetBuildResult(xml, moduleSet.modules.size)
}

/**
 * Cleans up orphaned module set XML files that no longer have corresponding Kotlin module set functions.
 * Scans each output directory and deletes files matching the module set pattern that aren't in the generated set.
 *
 * @param outputDirToGeneratedFiles Map of output directory to set of generated file names
 * @param strategy File update strategy (actual deletion or dry run recording)
 * @return List of deleted file results
 */
internal fun cleanupOrphanedModuleSetFiles(
  outputDirToGeneratedFiles: Map<Path, Set<String>>,
  strategy: FileUpdateStrategy,
): List<ModuleSetFileResult> {
  val deletedFiles = mutableListOf<ModuleSetFileResult>()

  for ((dir, generatedFiles) in outputDirToGeneratedFiles) {
    if (Files.exists(dir)) {
      // First, collect all module set files in the directory
      val allModuleSetFiles = Files.list(dir).use { stream ->
        stream
          .filter { Files.isRegularFile(it) }
          .filter { it.fileName.toString().startsWith(MODULE_SET_PREFIX) && it.fileName.toString().endsWith(".xml") }
          .toList()
      }

      // Identify orphaned files
      val orphanedFiles = allModuleSetFiles.filter { it.fileName.toString() !in generatedFiles }

      // Safety check: prevent mass deletion if >50% of files appear orphaned
      if (orphanedFiles.size > allModuleSetFiles.size * 0.5 && orphanedFiles.isNotEmpty()) {
        error("""
          |Safety check failed: Too many orphaned module set files detected in $dir
          |  Total module set files: ${allModuleSetFiles.size}
          |  Orphaned files: ${orphanedFiles.size}
          |  Generated files tracked: $generatedFiles
          |  Orphaned file names: ${orphanedFiles.map { it.fileName }}
          |This might indicate a bug in module set discovery or tracking. Aborting cleanup.
        """.trimMargin())
      }

      // Delete orphaned files with logging
      for (filePath in orphanedFiles) {
        val fileName = filePath.fileName.toString()
        println("Deleting orphaned module set file: $fileName from $dir")
        println("  Reason: File not in generated set: $generatedFiles")
        strategy.delete(filePath)
        deletedFiles.add(ModuleSetFileResult(fileName, FileChangeStatus.DELETED, 0))
      }
    }
  }

  return deletedFiles
}

/**
 * Resolves the output directory for a module set.
 * If the module set has an outputModule specified, uses that module's resources directory.
 * Otherwise, uses the default outputDir.
 */
private fun resolveOutputDir(moduleSet: ModuleSet, defaultOutputDir: Path, outputProvider: ModuleOutputProvider?): Path {
  val outputModuleName = moduleSet.outputModule
  if (outputModuleName != null) {
    require(outputProvider != null) {
      "ModuleOutputProvider is required when module set '${moduleSet.name}' specifies outputModule='$outputModuleName'"
    }
    val module = outputProvider.findRequiredModule(outputModuleName)
    val resourceRoot = module.sourceRoots.firstOrNull { it.rootType == JavaResourceRootType.RESOURCE }
      ?: error("No resource root found for module '$outputModuleName' (required by module set '${moduleSet.name}')")
    return resourceRoot.path.resolve("META-INF")
  }
  return defaultOutputDir
}

/**
 * Generates all module set XMLs for the given object and returns statistics.
 * Internal function for use by multi-set generators.
 *
 * Automatically cleans up outdated module set XML files that no longer have corresponding
 * Kotlin module set functions.
 */
internal suspend fun doGenerateAllModuleSetsInternal(
  obj: Any,
  outputDir: Path,
  label: String,
  outputProvider: ModuleOutputProvider? = null,
  strategy: FileUpdateStrategy,
): ModuleSetGenerationResult = coroutineScope {
  Files.createDirectories(outputDir)

  val moduleSets = discoverModuleSets(obj)

  // Generate all module set XML files first (in parallel)
  val fileResults = moduleSets.map { moduleSet ->
    async {
      val targetOutputDir = resolveOutputDir(moduleSet, outputDir, outputProvider)
      generateModuleSetXml(moduleSet = moduleSet, outputDir = targetOutputDir, label = label, strategy = strategy)
    }
  }.awaitAll()

  // Build map of output directory -> generated file names (for cleanup aggregation)
  val outputDirToGeneratedFiles = HashMap<Path, MutableSet<String>>()
  for ((moduleSet, fileResult) in moduleSets.zip(fileResults)) {
    val targetOutputDir = resolveOutputDir(moduleSet, outputDir, outputProvider)
    outputDirToGeneratedFiles.computeIfAbsent(targetOutputDir) { HashSet() }.add(fileResult.fileName)
  }

  // Return results with tracking map (cleanup will be done after aggregating all labels)
  ModuleSetGenerationResult(label = label, outputDir = outputDir, files = fileResults, trackingMap = outputDirToGeneratedFiles)
}