// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import com.intellij.platform.distributionContent.PlatformContentModuleRow
import com.intellij.platform.distributionContent.PlatformJarRow
import com.intellij.platform.distributionContent.PlatformLibraryRow
import com.intellij.platform.distributionContent.PlatformMergedLibraryRow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.getLibraryFileName
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.productLayout.util.isProductionRuntimeDependency
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

/** The `[platform_jars]`, `[platform_libraries]`, `[platform_merged_libraries]` and `[platform_content_modules]` rows of one product. */
@Internal
class PlatformJarRows(
  @JvmField val jars: List<PlatformJarRow>,
  @JvmField val libraries: List<PlatformLibraryRow>,
  @JvmField val mergedLibraries: List<PlatformMergedLibraryRow>,
  @JvmField val contentModules: List<PlatformContentModuleRow>,
)

/**
 * The jars under `lib/` that [layout] states for [product].
 *
 * One [PlatformJarRow] per relative output file, in the order the layout first names the file. The members of a jar
 * keep the [PlatformLayout.includedModules] order. One [PlatformContentModuleRow] per included module whose reason
 * [ModuleIncludeReasons.isProductModule] accepts, which is the test the content report uses to split the product
 * modules from the platform.
 *
 * One [PlatformLibraryRow] per project library and per module library the layout declares, with the jar path the
 * declaration names, or no path for an empty one. One [PlatformMergedLibraryRow] per library the packer merges into a
 * member's jar, with that jar's path; [mergedLibraryNames] holds the rule. [findModule] resolves a member to its JPS
 * module.
 */
@Internal
fun derivePlatformJars(product: String, layout: PlatformLayout, findModule: (String) -> JpsModule): PlatformJarRows {
  val membersByJar = LinkedHashMap<String, MutableList<String>>()
  val contentModules = LinkedHashSet<PlatformContentModuleRow>()
  val libraries = LinkedHashSet<PlatformLibraryRow>()
  val mergedLibraries = LinkedHashSet<PlatformMergedLibraryRow>()
  for (library in layout.getIncludedProjectLibraries()) {
    libraries.add(PlatformLibraryRow(product = product, library = library.libraryName, relativeOutputFile = library.outPath))
  }
  for ((moduleName, libraryName, relativeOutputPath) in layout.getIncludedModuleLibraries()) {
    libraries.add(PlatformLibraryRow(
      product = product,
      library = libraryName,
      relativeOutputFile = relativeOutputPath.takeIf { it.isNotEmpty() },
      moduleName = moduleName,
    ))
  }
  for (item in layout.includedModules) {
    membersByJar.computeIfAbsent(item.relativeOutputFile) { ArrayList() }.add(item.moduleName)
    if (ModuleIncludeReasons.isProductModule(item.reason)) {
      contentModules.add(PlatformContentModuleRow(product = product, module = item.moduleName))
    }
    for (libraryName in mergedLibraryNames(item = item, module = findModule(item.moduleName), layout = layout)) {
      mergedLibraries.add(PlatformMergedLibraryRow(product = product, library = libraryName, relativeOutputFile = item.relativeOutputFile))
    }
  }
  val jars = membersByJar.map { (relativeOutputFile, members) ->
    PlatformJarRow(product = product, relativeOutputFile = relativeOutputFile, members = members)
  }
  return PlatformJarRows(jars = jars, libraries = libraries.toList(), mergedLibraries = mergedLibraries.toList(), contentModules = contentModules.toList())
}

/** Derives the product's complete platform facts without compiled output or resource execution. */
@Internal
fun derivePlatformJars(product: String, productProperties: ProductProperties, outputProvider: ModuleOutputProvider): PlatformJarRows {
  return derivePlatformJars(product, createPlatformLayout(productProperties, outputProvider), outputProvider::findRequiredModule)
}

/** Requires content ownership except for the exact ordered members of [retainedJars]. */
@Internal
@Suppress("ReplaceGetOrSet")
fun validatePlatformContentOwnership(
  modules: Collection<ModuleItem>,
  retainedJars: Map<String, List<String>>,
  explicitModuleNames: Collection<String> = emptyList(),
) {
  val errors = ArrayList<String>()
  for ((name, owners) in modules.groupBy { it.moduleName }) {
    if (owners.size > 1) {
      errors.add("Duplicate platform ownership for $name: $owners")
    }
  }
  val contentModules = modules.filter { ModuleIncludeReasons.isProductModule(it.reason) }.mapTo(HashSet()) { it.moduleName }
  for (name in explicitModuleNames) {
    if (name in contentModules) {
      errors.add("Content module $name also has explicit platform ownership")
    }
  }
  val nonContentModules = modules.filterNot { ModuleIncludeReasons.isProductModule(it.reason) }
  for (item in nonContentModules) {
    if (retainedJars.get(item.relativeOutputFile)?.contains(item.moduleName) != true) {
      errors.add("Missing content ownership: ${item.moduleName} in ${item.relativeOutputFile}; ${item.reason}")
    }
  }
  val actualJars = modules.groupBy({ it.relativeOutputFile }, { it.moduleName })
  val nonContentJars = nonContentModules.groupBy({ it.relativeOutputFile }, { it.moduleName })
  for ((path, members) in retainedJars) {
    for (name in members) {
      if (name !in nonContentJars.get(path).orEmpty()) {
        errors.add("Stale content ownership exception: $name in $path")
      }
    }
    if (actualJars.get(path) != members) {
      errors.add("Changed retained jar $path: expected $members, actual ${actualJars.get(path)}")
    }
  }
  check(errors.isEmpty()) { errors.joinToString("\n") }
}

/**
 * The names of the libraries the platform packer merges into the jar of [item], in the order [module] declares them.
 *
 * This is a copy of the platform half of the packer's library rule, kept apart so the packaging gate compares two
 * producers. A library counts when its dependency reaches the production runtime. A module library is merged unless the
 * layout declares it without an extra copy. A project library is merged only into a product module's jar, and only when
 * the layout does not declare it and no module of the same group already brings it; see [sameGroupDependencyDeclares].
 * The name is the one the distribution knows the library by.
 */
@Internal
fun mergedLibraryNames(item: ModuleItem, module: JpsModule, layout: PlatformLayout): List<String> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val result = ArrayList<String>()
  for (element in module.dependenciesList.dependencies) {
    if (element !is JpsLibraryDependency || !isProductionRuntimeDependency(element = element, javaExtensionService = javaExtensionService)) {
      continue
    }
    val reference = element.libraryReference
    if (reference.parentReference !is JpsModuleReference) {
      val name = reference.libraryName
      if (!ModuleIncludeReasons.isProductModule(item.reason) ||
          layout.hasLibrary(name) ||
          sameGroupDependencyDeclares(module = module, libraryName = name, layout = layout)) {
        continue
      }
    }
    val library = requireNotNull(element.library) { "cannot find $reference" }
    val libraryName = getLibraryFileName(library)
    if (layout.getIncludedModuleLibraries().any { it.libraryName == libraryName && !it.extraCopy }) {
      continue
    }
    result.add(libraryName)
  }
  return result
}

/**
 * Whether a production module dependency of [module] in the same name group declares [libraryName] itself.
 *
 * The group is the module name without its last segment. The dependency counts when it is the group module, or when it
 * is in the group and [layout] does not include it. That module's jar then carries the library, and this jar does not.
 */
private fun sameGroupDependencyDeclares(module: JpsModule, libraryName: String, layout: PlatformLayout): Boolean {
  val parentGroup = module.name.substringBeforeLast('.', missingDelimiterValue = "")
  if (parentGroup.isEmpty()) {
    return false
  }
  val prefix = "$parentGroup."
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  for (dependency in module.getProductionModuleDependencies()) {
    val dependencyName = dependency.moduleReference.moduleName
    if (dependencyName != parentGroup && !(dependencyName.startsWith(prefix) && layout.includedModules.none { it.moduleName == dependencyName })) {
      continue
    }
    val dependencyModule = dependency.module ?: continue
    val declares = dependencyModule.dependenciesList.dependencies.any {
      it is JpsLibraryDependency &&
      it.libraryReference.libraryName == libraryName &&
      isProductionRuntimeDependency(element = it, javaExtensionService = javaExtensionService)
    }
    if (declares) {
      return true
    }
  }
  return false
}
