// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.BuildPaths
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
  @JvmField val loading: ModuleLoadingRule? = null,
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
 */
@Serializable
data class ModuleSet(
  @JvmField val name: String,
  @JvmField val modules: List<ContentModule>,
  @JvmField val nestedSets: List<ModuleSet> = emptyList(),
  @JvmField val alias: String? = null,
)

/**
 * Interface for module set providers that can generate XML files.
 * Provides the output directory where generated module set XML files are stored.
 */
interface ModuleSetProvider {
  /**
   * Returns the path to the META-INF directory where this provider's module set XML files are generated.
   */
  fun getOutputDirectory(paths: BuildPaths): Path
}

/**
 * DSL builder for creating ModuleSets with reduced boilerplate.
 */
@ProductDslMarker
class ModuleSetBuilder {
  private val modules = mutableListOf<ContentModule>()
  private val nestedSets = mutableListOf<ModuleSet>()

  /**
   * Add a single module.
   */
  fun module(name: String, loading: ModuleLoadingRule? = null) {
    modules.add(ContentModule(name, loading))
  }

  /**
   * Add a single module with EMBEDDED loading.
   */
  fun embeddedModule(name: String, includeDependencies: Boolean = false) {
    modules.add(ContentModule(name, ModuleLoadingRule.EMBEDDED, includeDependencies))
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
 * ```
 */
inline fun moduleSet(name: String, alias: String? = null, block: ModuleSetBuilder.() -> Unit): ModuleSet {
  val (modules, nestedSets) = ModuleSetBuilder().apply(block).build()
  return ModuleSet(name, modules, nestedSets, alias)
}

/**
 * Appends a single module XML element to the StringBuilder.
 */
private fun appendModuleXml(sb: StringBuilder, module: ContentModule) {
  sb.append("    <module name=\"${module.name}\"")
  if (module.loading == ModuleLoadingRule.EMBEDDED) {
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
    append("<!-- To regenerate, run: ${mainClass}.main() -->\n")
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
 * Generates all module set XMLs for the given object.
 * Discovers all ModuleSet functions via reflection, generates XML files, and prints results.
 *
 * @param obj The object containing module set functions (e.g., CommunityModuleSets, UltimateModuleSets)
 * @param outputDir Directory where XML files will be generated
 * @param label Description label for logging (e.g., "community", "ultimate")
 * @param printSummary Whether to print generation summary (default: true)
 */
fun generateAllModuleSets(obj: Any, outputDir: Path, label: String, printSummary: Boolean = true) {
  val startTime = System.currentTimeMillis()
  Files.createDirectories(outputDir)

  val moduleSets = discoverModuleSets(obj)
  val fileResults = moduleSets.map { moduleSet ->
    generateModuleSetXml(moduleSet, outputDir, label)
  }

  if (printSummary) {
    val result = ModuleSetGenerationResult(label, outputDir, fileResults)
    printGenerationSummary(moduleSetResults = listOf(result), productResult = null, durationMs = System.currentTimeMillis() - startTime)
  }
}

/**
 * Generates all module set XMLs for the given object and returns statistics.
 * Internal function for use by multi-set generators.
 */
fun doGenerateAllModuleSetsInternal(obj: Any, outputDir: Path, label: String): ModuleSetGenerationResult {
  Files.createDirectories(outputDir)

  val moduleSets = discoverModuleSets(obj)
  val fileResults = moduleSets.map { moduleSet ->
    generateModuleSetXml(moduleSet = moduleSet, outputDir = outputDir, label = label)
  }

  return ModuleSetGenerationResult(label = label, outputDir = outputDir, files = fileResults)
}