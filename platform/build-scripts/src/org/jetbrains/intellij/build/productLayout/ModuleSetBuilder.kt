// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import org.jetbrains.intellij.build.BuildPaths
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a named collection of content modules.
 * The name serves as metadata for debugging and XML generation (used in the 'source' attribute).
 *
 * @param name Identifier for the module set (e.g., "essential", "vcs", "ssh")
 * @param modules List of content modules in this set
 * @param nestedSets List of nested module sets (for xi:include generation)
 * @param alias Optional module alias for `<module value="..."/>` declaration (e.g., "com.intellij.modules.xml")
 */
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
  fun embeddedModule(name: String) {
    modules.add(ContentModule(name, ModuleLoadingRule.EMBEDDED))
  }

  /**
   * Include all modules from another ModuleSet.
   */
  fun moduleSet(set: ModuleSet) {
    modules.addAll(set.modules)
    nestedSets.add(set)
  }

  @PublishedApi
  internal fun build(): Pair<List<ContentModule>, List<ModuleSet>> = Pair(modules, nestedSets)
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
 * Builds a `<module value="..."/>` declaration if alias is provided.
 * @param alias The module alias (e.g., "com.intellij.modules.xml")
 * @return XML string, or empty string if alias is null
 */
internal fun buildModuleAliasXml(alias: String?): String {
  return if (alias == null) "" else "  <module value=\"$alias\"/>\n"
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
 * Recursively collects all module aliases from a module set and its nested sets.
 */
private fun collectAllAliases(moduleSet: ModuleSet): List<String> {
  val aliases = mutableListOf<String>()
  if (moduleSet.alias != null) {
    aliases.add(moduleSet.alias)
  }
  moduleSet.nestedSets.forEach { aliases.addAll(collectAllAliases(it)) }
  return aliases
}

/**
 * Recursively appends modules from a module set, including nested sets.
 * Handles nested sets at any depth with breadcrumb trail showing full hierarchy.
 */
private fun appendModuleSetContent(sb: StringBuilder, moduleSet: ModuleSet, indent: String = "    ", breadcrumb: String = "") {
  // Get direct modules (not from nested sets)
  val nestedModuleNames = moduleSet.nestedSets.flatMap { it.modules.map { m -> m.name } }.toHashSet()
  val directModules = moduleSet.modules.filter { it.name !in nestedModuleNames }

  // Recursively append nested sets first
  for (nestedSet in moduleSet.nestedSets) {
    // Build breadcrumb path
    val nestedBreadcrumb = if (breadcrumb.isEmpty()) {
      nestedSet.name
    } else {
      "$breadcrumb > ${nestedSet.name}"
    }

    sb.append("$indent<!-- nested: $nestedBreadcrumb -->\n")
    appendModuleSetContent(sb, nestedSet, indent, nestedBreadcrumb) // RECURSIVE CALL with breadcrumb
    sb.append("\n")
  }

  // Then append direct modules
  if (directModules.isNotEmpty()) {
    if (moduleSet.nestedSets.isNotEmpty()) {
      sb.append("$indent<!-- direct modules -->\n")
    }
    directModules.forEach { appendModuleXml(sb, it) }
  }
}

/**
 * Builds the XML content for a module set.
 *
 * @param moduleSet The module set to build XML for
 * @param label Description label ("community" or "ultimate") for header generation
 * @return XML string representation of the module set
 */
internal fun buildModuleSetXml(moduleSet: ModuleSet, label: String): String {
  val sb = StringBuilder()

  // Add generated file header
  val mainClass = if (label == "community") "CommunityModuleSets" else "UltimateModuleSets"
  sb.append("<!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
  sb.append("<!-- To regenerate, run: ${mainClass}.main() -->\n")
  sb.append("<!-- Source: see moduleSet(\"${moduleSet.name}\") function in ${mainClass}.kt -->\n")
  sb.append("<!-- Note: Files are kept under VCS to support running products without dev mode (deprecated) -->\n")

  // Opening tag (no xmlns:xi needed since we inline nested sets)
  sb.append("<idea-plugin>")
  sb.append("\n")

  // Output all module aliases (recursively collected from this set and nested sets)
  val allAliases = collectAllAliases(moduleSet)
  if (allAliases.isNotEmpty()) {
    allAliases.forEach { sb.append("  <module value=\"$it\"/>\n") }
    sb.append("\n")
  }

  // Collect all modules (nested + direct) and output in a single content block
  val hasAnyModules = moduleSet.nestedSets.isNotEmpty() || moduleSet.modules.isNotEmpty()
  if (hasAnyModules) {
    sb.append("  <content namespace=\"jetbrains\">")
    sb.append("\n")

    // Recursively append all content (handles nested sets at any depth)
    appendModuleSetContent(sb, moduleSet)

    sb.append("  </content>")
    sb.append("\n")
  }

  // Closing tag - NO trailing newline
  sb.append("</idea-plugin>")

  return sb.toString()
}

/**
 * Discovers all module set functions in the given object using reflection.
 * Returns all public functions that:
 * - Return ModuleSet
 * - Take no parameters
 * - Are not named 'main'
 *
 * @param obj The object to scan for module set functions (e.g., CommunityModuleSets, UltimateModuleSets)
 * @return List of all discovered ModuleSets
 */
private fun discoverModuleSets(obj: Any): List<ModuleSet> {
  val lookup = MethodHandles.lookup()
  val clazz = obj.javaClass
  val methodType = MethodType.methodType(ModuleSet::class.java)

  val declaredMethods = clazz.declaredMethods
  val result = ArrayList<ModuleSet>(declaredMethods.size)
  for (method in declaredMethods) {
    if (method.parameterCount == 0 && java.lang.reflect.Modifier.isPublic(method.modifiers) && method.returnType == ModuleSet::class.java) {
      val moduleSet = lookup.findVirtual(clazz, method.name, methodType).invoke(obj) as ModuleSet
      result.add(moduleSet)
    }
  }
  return result
}

/**
 * Builds a reverse index mapping each module name to the list of module sets that contain it.
 * Recursively walks through all module sets and their nested sets.
 *
 * This is useful for tracking which module set(s) a module belongs to, especially when
 * module sets are inlined (no xi:include) and runtime tracking is unavailable.
 *
 * @param moduleSetProviders List of objects containing module set definitions (e.g., UltimateModuleSets, CommunityModuleSets)
 * @return Map from module name to list of module set names (with "intellij.moduleSets." prefix)
 */
fun buildModuleToSetMapping(moduleSetProviders: List<Any>): Map<String, List<String>> {
  val moduleToSets = mutableMapOf<String, MutableList<String>>()
  val processedSets = mutableSetOf<String>()

  /**
   * Recursively collects modules from a module set and its nested sets.
   */
  fun collectModulesRecursively(set: ModuleSet, setName: String) {
    // Skip if already processed (prevents duplicates when a module set is both top-level and nested)
    if (!processedSets.add(setName)) {
      return
    }

    // Get direct modules (not from nested sets)
    val nestedModuleNames = set.nestedSets.flatMap { it.modules.map { m -> m.name } }.toHashSet()
    val directModules = set.modules.filter { it.name !in nestedModuleNames }

    // Add direct modules to this set
    for (module in directModules) {
      moduleToSets.computeIfAbsent(module.name) { mutableListOf() }.add(setName)
    }

    // Recursively process nested sets
    for (nestedSet in set.nestedSets) {
      collectModulesRecursively(nestedSet, "intellij.moduleSets.${nestedSet.name}")
    }
  }

  // Process all providers
  for (provider in moduleSetProviders) {
    val moduleSets = discoverModuleSets(provider)
    // Process all top-level module sets from this provider
    for (moduleSet in moduleSets) {
      collectModulesRecursively(moduleSet, "intellij.moduleSets.${moduleSet.name}")
    }
  }

  return moduleToSets
}

/**
 * Builds a mapping of parent module sets to their nested module sets.
 * This preserves the hierarchical structure after inlining.
 *
 * @param moduleSetProviders List of objects containing module set definitions (e.g., UltimateModuleSets, CommunityModuleSets)
 * @return Map from module set name to set of nested module set names (with "intellij.moduleSets." prefix)
 */
fun buildModuleSetIncludes(moduleSetProviders: List<Any>): Map<String, Set<String>> {
  val moduleSetToIncludes = mutableMapOf<String, MutableSet<String>>()
  val processedSets = mutableSetOf<String>()

  /**
   * Recursively collects nested module sets.
   */
  fun collectNestedSetsRecursively(set: ModuleSet, setName: String) {
    // Skip if already processed (prevents duplicates when a module set is both top-level and nested)
    if (!processedSets.add(setName)) {
      return
    }

    if (set.nestedSets.isNotEmpty()) {
      val includes = moduleSetToIncludes.computeIfAbsent(setName) { mutableSetOf() }
      for (nestedSet in set.nestedSets) {
        val nestedSetName = "intellij.moduleSets.${nestedSet.name}"
        includes.add(nestedSetName)
        // Recursively process nested sets
        collectNestedSetsRecursively(nestedSet, nestedSetName)
      }
    }
  }

  // Process all providers
  for (provider in moduleSetProviders) {
    val moduleSets = discoverModuleSets(provider)
    // Process all top-level module sets from this provider
    for (moduleSet in moduleSets) {
      collectNestedSetsRecursively(moduleSet, "intellij.moduleSets.${moduleSet.name}")
    }
  }

  return moduleSetToIncludes
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