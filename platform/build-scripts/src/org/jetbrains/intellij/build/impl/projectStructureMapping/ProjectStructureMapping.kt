// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.ModuleLibraryFile
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProjectLibraryFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.generateInclusionReasonForContentModule
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import java.io.File
import java.nio.file.Path
import java.util.TreeMap
import com.intellij.platform.distributionContent.ProjectLibraryEntry as ProjectedProjectLibraryEntry

internal fun getIncludedModules(entries: Sequence<DistributionFileEntry>): Sequence<String> {
  return entries.mapNotNull { (it as? ModuleOutputEntry)?.owner?.moduleName }.distinct()
}

/**
 * The packed content of one distribution build, in the shape the content checks read.
 *
 * [platform] lists every platform file and every dist file, and ends with the `plugins` entry that indexes the
 * product modules and the plugins. [productModules] lists the product modules as plugin reports. [bundled] and
 * [nonBundled] list the plugins, one report per main module, OS and architecture.
 *
 * The packaging tests read this projection in memory. No build step serializes it.
 */
@ApiStatus.Internal
class ProjectedContentReport(
  @JvmField val platform: List<FileEntry>,
  @JvmField val productModules: List<PluginContentReport>,
  @JvmField val bundled: List<PluginContentReport>,
  @JvmField val nonBundled: List<PluginContentReport>,
)

private typealias ProductModuleEntries = List<Pair<ModuleItem, List<DistributionFileEntry>>>

private fun buildRootModuleSets(productModules: ProductModuleEntries): Map<String, ProductModuleEntries> {
  val allModuleSets = TreeMap<String, MutableList<Pair<ModuleItem, List<DistributionFileEntry>>>>()
  val nestedModuleSetNames = mutableSetOf<String>()

  // Single pass: group modules by their module sets and identify nested sets.
  for ((moduleItem, distEntries) in productModules) {
    val chain = moduleItem.moduleSet ?: continue

    // A module should be included in all sets in its chain.
    // Sets after position 0 are nested (for example [A, B] means B is nested).
    for ((index, setName) in chain.withIndex()) {
      allModuleSets.computeIfAbsent(setName) { mutableListOf() }.add(moduleItem to distEntries)
      if (index > 0) {
        nestedModuleSetNames.add(setName)
      }
    }
  }

  return allModuleSets.filterKeys { it !in nestedModuleSetNames }.toSortedMap()
}

/**
 * Projects the in-memory [contentReport] into the shape the content checks read.
 *
 * The caller takes this snapshot after shared files are copied and registered, before OS-specific assembly starts.
 */
internal fun projectContentReport(contentReport: ContentReport, context: BuildContext): ProjectedContentReport {
  val buildPaths = context.paths
  val (fileToEntry, productModules) = groupPlatformEntries(contentReport = contentReport, buildPaths = buildPaths)
  val rootModuleSets = buildRootModuleSets(productModules)
  return ProjectedContentReport(
    platform = projectPlatform(
      contentReport = contentReport,
      buildPaths = buildPaths,
      distFiles = context.getDistFiles(os = null, arch = null, libcImpl = null),
      fileToEntry = fileToEntry,
      productModules = productModules,
      moduleSets = rootModuleSets,
    ),
    productModules = projectProductModules(productModules, buildPaths),
    bundled = projectPlugins(contentReport.bundledPlugins, buildPaths),
    nonBundled = projectPlugins(contentReport.nonBundledPlugins, buildPaths),
  )
}

/** The first build result per main module, OS and architecture wins. */
private fun distinctPlugins(pluginToEntries: List<PluginBuildResult>): List<PluginBuildResult> {
  val written = HashSet<String>()
  return pluginToEntries.filter { written.add(createPluginKey(it)) }
}

private fun projectPlugins(pluginToEntries: List<PluginBuildResult>, buildPaths: BuildPaths): List<PluginContentReport> {
  return distinctPlugins(pluginToEntries).map { buildResult ->
    val fileToPresentablePath = HashMap<Path, String>()

    val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
    for (entry in buildResult.distribution) {
      if (entry is CustomAssetEntry && entry.relativeOutputFile != null && !entry.path.startsWith(buildResult.dir)) {
        continue
      }
      val presentablePath = if (entry is CustomAssetEntry && entry.relativeOutputFile != null) {
        entry.relativeOutputFile
      }
      else {
        fileToPresentablePath.computeIfAbsent(entry.path) {
          if (entry.path.startsWith(buildResult.dir)) {
            buildResult.dir.relativize(entry.path).toString().replace(File.separatorChar, '/')
          }
          else {
            shortenAndNormalizePath(it, buildPaths)
          }
        }
      }
      fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
    }

    val contentModuleReason = generateInclusionReasonForContentModule(buildResult.mainModule)
    PluginContentReport(
      mainModule = buildResult.mainModule,
      os = buildResult.os?.osId,
      arch = buildResult.arch?.name,
      content = projectContentEntries(fileToEntry = fileToEntry, buildPaths = buildPaths) { entries ->
        ModuleLists(
          modules = projectModules(
            fileEntries = entries,
            buildPaths = buildPaths,
            reasonFilter = { it.reason != contentModuleReason },
          ),
          contentModules = projectModules(
            fileEntries = entries,
            buildPaths = buildPaths,
            reasonFilter = { it.reason == contentModuleReason },
            withReason = false,
          ),
        )
      },
    )
  }
}

private fun ModuleItem.isSubjectToDoubleNaming(): Boolean {
  return moduleName.contains(".rd.") || moduleName == "intellij.platform.split.protocol"
}

private fun projectProductModules(productModuleMap: ProductModuleEntries, buildPaths: BuildPaths): List<PluginContentReport> {
  val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
  val fileToPresentablePath = HashMap<Path, String>()

  return productModuleMap.map { (moduleItem, entries) ->
    fileToPresentablePath.clear()
    fileToEntry.clear()

    for (entry in entries) {
      val file = entry.path
      // the issue is that some modules embedded into some products (Rider), so, name maybe product.jar...
      val presentablePath = if (moduleItem.isSubjectToDoubleNaming() && (entry as ModuleOwnedFileEntry).owner!!.moduleName == moduleItem.moduleName) {
        "<file>"
      }
      else {
        fileToPresentablePath.computeIfAbsent(file) {
          shortenAndNormalizePath(it, buildPaths)
        }
      }
      fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
    }

    PluginContentReport(
      mainModule = moduleItem.moduleName,
      content = projectContentEntries(fileToEntry = fileToEntry, buildPaths = buildPaths) { entries ->
        // module maybe embedded in one product and not embedded in another one (rider case)
        ModuleLists(modules = projectModules(fileEntries = entries, buildPaths = buildPaths, withReason = false))
      },
    )
  }
}

private fun projectPlatform(
  contentReport: ContentReport,
  buildPaths: BuildPaths,
  distFiles: Collection<DistFile>,
  fileToEntry: Map<String, List<DistributionFileEntry>>,
  productModules: ProductModuleEntries,
  moduleSets: Map<String, ProductModuleEntries>,
): List<FileEntry> {
  val result = ArrayList<FileEntry>()
  for ((filePath, fileEntries) in fileToEntry) {
    result.add(FileEntry(
      name = filePath,
      projectLibraries = projectProjectLibraries(grouped = groupProjectLibraries(fileEntries), buildPaths = buildPaths),
      modules = projectModules(fileEntries = fileEntries, buildPaths = buildPaths),
    ))
  }

  for (item in distFiles) {
    result.add(FileEntry(
      name = item.relativePath,
      os = item.os?.osId,
      arch = item.arch?.dirName,
      libc = item.libcImpl?.toString(),
    ))
  }

  result.add(FileEntry(
    name = "plugins",
    productModules = collectProductModules(productModules = productModules, moduleSets = moduleSets, kind = ModuleIncludeReasons.PRODUCT_MODULES),
    productEmbeddedModules = collectProductModules(productModules = productModules, moduleSets = moduleSets, kind = ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES),
    bundled = projectPluginIndex(contentReport.bundledPlugins),
    nonBundled = projectPluginIndex(contentReport.nonBundledPlugins),
  ))
  return result
}

private fun projectPluginIndex(plugins: List<PluginBuildResult>): List<PluginContentReport> {
  return distinctPlugins(plugins).map {
    PluginContentReport(mainModule = it.mainModule, os = it.os?.osId, arch = it.arch?.name)
  }
}

private fun groupPlatformEntries(
  contentReport: ContentReport,
  buildPaths: BuildPaths,
): Pair<Map<String, List<DistributionFileEntry>>, ProductModuleEntries> {
  val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
  val productModuleToEntries = HashMap<ModuleItem, MutableList<DistributionFileEntry>>()
  val fileToPresentablePath = HashMap<Path, String>()

  // First pass: identify container modules with includeDependencies=true
  val containerModules = HashMap<String, ModuleItem>() // module name -> ModuleItem
  for (entry in contentReport.platform) {
    if (entry is ModuleOwnedFileEntry) {
      val owner = entry.owner
      if (owner != null && owner.isProductModule() && owner.includeDependencies) {
        containerModules[owner.moduleName] = owner
      }
    }
  }

  // Build a map from dependency module name to its root container module
  val dependencyToContainer = HashMap<String, ModuleItem>()
  for (entry in contentReport.platform) {
    if (entry is ModuleOwnedFileEntry) {
      val owner = entry.owner
      if (owner != null && owner.isProductModule()) {
        val reason = owner.reason
        // Check if this is a dependency module (reason starts with PRODUCT_EMBEDDED_MODULES + " <- ")
        if (reason != null && reason.startsWith(ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES + " <- ")) {
          // Extract the root container module from the reason chain
          // Reason format: "productEmbeddedModule <- dep <- ... <- container"
          // The last element is the root container
          val chain = reason.substring((ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES + " <- ").length).split(" <- ")
          if (chain.isNotEmpty()) {
            val rootContainerName = chain.last()
            val containerModule = containerModules[rootContainerName]
            if (containerModule != null) {
              dependencyToContainer[owner.moduleName] = containerModule
            }
          }
        }
      }
    }
  }

  // Second pass: group entries, aggregating dependencies into their containers
  for (entry in contentReport.platform) {
    if (entry is ModuleOwnedFileEntry) {
      val owner = entry.owner
      if (owner != null && ModuleIncludeReasons.isProductModule(owner.reason)) {
        // Check if this module is a dependency of a container module
        val targetOwner = dependencyToContainer[owner.moduleName] ?: owner
        productModuleToEntries.computeIfAbsent(targetOwner) { mutableListOf() }.add(entry)
        continue
      }
    }

    val presentablePath = fileToPresentablePath.computeIfAbsent(entry.path) {
      shortenAndNormalizePath(it, buildPaths)
    }
    fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
  }
  return fileToEntry to productModuleToEntries.toList().sortedBy { it.first.moduleName }
}

private fun collectModulesInUsedSets(productModules: ProductModuleEntries, moduleSets: Map<String, *>): Set<String> {
  val usedSetNames = moduleSets.keys  // Already a Set
  return productModules
    .asSequence()
    .filter { (item) -> item.moduleSet?.any { it in usedSetNames } == true }
    .mapTo(mutableSetOf()) { it.first.moduleName }
}

/** The module set names first, for [ModuleIncludeReasons.PRODUCT_MODULES], then every module of [kind] outside a used set. */
private fun collectProductModules(productModules: ProductModuleEntries, kind: String, moduleSets: Map<String, ProductModuleEntries>): List<String> {
  val result = ArrayList<String>()
  if (kind == ModuleIncludeReasons.PRODUCT_MODULES) {
    result.addAll(moduleSets.keys)
  }

  val modulesInUsedSets = collectModulesInUsedSets(productModules, moduleSets)
  for ((item) in productModules) {
    if (item.reason == kind && item.moduleName !in modulesInUsedSets) {
      result.add(item.moduleName)
    }
  }
  return result
}

private fun shortenAndNormalizePath(file: Path, buildPaths: BuildPaths, extraRoot: Path? = null): String {
  val shortened = when {
    file.startsWith(MAVEN_REPO) -> $$"$MAVEN_REPOSITORY$/" + MAVEN_REPO.relativize(file).toString()
    file.startsWith(buildPaths.projectHome) -> $$"$PROJECT_DIR$/" + buildPaths.projectHome.relativize(file).toString()
    file.startsWith(buildPaths.buildOutputDir) -> buildPaths.buildOutputDir.relativize(file).toString()
    extraRoot != null && file.startsWith(extraRoot) -> extraRoot.relativize(file).toString()
    else -> file.toString()
  }

  val normalized = shortened.replace(File.separatorChar, '/')
  return if (normalized.startsWith("temp/")) normalized.substring("temp/".length) else normalized
}

/**
 * One [ModuleEntry] per module output in [fileEntries] that passes [reasonFilter], with the module libraries of that
 * module in the same file. A [ModuleIncludeReasons.PRODUCT_MODULES] reason is obvious and stays out.
 */
private fun projectModules(
  fileEntries: List<DistributionFileEntry>,
  buildPaths: BuildPaths,
  withReason: Boolean = true,
  reasonFilter: (ModuleOutputEntry) -> Boolean = { true },
): List<ModuleEntry> {
  val result = ArrayList<ModuleEntry>()
  for (entry in fileEntries) {
    if (entry !is ModuleOutputEntry || !reasonFilter(entry)) {
      continue
    }

    val moduleName = entry.owner.moduleName
    result.add(ModuleEntry(
      name = moduleName,
      size = entry.size,
      reason = entry.reason?.takeIf { withReason && it != ModuleIncludeReasons.PRODUCT_MODULES },
      libraries = projectModuleLibraries(fileEntries = fileEntries, moduleName = moduleName, buildPaths = buildPaths),
    ))
  }
  return result
}

private fun projectModuleLibraries(fileEntries: List<DistributionFileEntry>, moduleName: String, buildPaths: BuildPaths): Map<String, List<ModuleLibraryFile>> {
  val filteredEntries = fileEntries.filter { it is ModuleLibraryFileEntry && it.moduleName == moduleName }
  val entriesGroupedByLibraryName = groupLibraryEntries<ModuleLibraryFileEntry>(filteredEntries) { it.libraryName }
  if (entriesGroupedByLibraryName.isEmpty()) {
    return emptyMap()
  }

  val result = LinkedHashMap<String, List<ModuleLibraryFile>>()
  for ((libName, entries) in entriesGroupedByLibraryName) {
    result[libName] = entries.map { ModuleLibraryFile(name = libraryFileName(it, buildPaths), size = it.size) }
  }
  return result
}

/** A file that holds one module library and nothing else. */
private fun projectSeparatePackedModuleLibrary(name: String, fileEntries: List<DistributionFileEntry>, buildPaths: BuildPaths): FileEntry {
  val entriesGroupedByLibraryName = groupLibraryEntries<ModuleLibraryFileEntry>(fileEntries) { it.libraryName }

  require(entriesGroupedByLibraryName.size == 1) {
    "Expected only one library, but got: $entriesGroupedByLibraryName"
  }

  val (libName, entries) = entriesGroupedByLibraryName.iterator().next()
  return FileEntry(
    name = name,
    library = libName,
    module = entries.first().moduleName,
    files = entries.map { ModuleLibraryFile(name = libraryFileName(it, buildPaths), size = it.size) },
  )
}

private fun libraryFileName(entry: LibraryFileEntry, buildPaths: BuildPaths): String {
  return entry.canonicalLibraryPath ?: shortenAndNormalizePath(entry.libraryFile!!, buildPaths)
}

/** The project libraries of a file, grouped by library name. */
private fun groupProjectLibraries(entries: List<DistributionFileEntry>): Map<ProjectLibraryData, List<ProjectLibraryEntry>> {
  val map = TreeMap<ProjectLibraryData, MutableList<ProjectLibraryEntry>> { o1, o2 ->
    o1.libraryName.compareTo(o2.libraryName)
  }
  for (entry in entries) {
    if (entry is ProjectLibraryEntry) {
      map.computeIfAbsent(entry.data) { mutableListOf() }.add(entry)
    }
  }
  return map
}

private fun projectProjectLibraries(grouped: Map<ProjectLibraryData, List<ProjectLibraryEntry>>, buildPaths: BuildPaths): List<ProjectedProjectLibraryEntry> {
  return grouped.map { (data, value) ->
    ProjectedProjectLibraryEntry(
      name = data.libraryName,
      files = value.map { ProjectLibraryFile(name = libraryFileName(it, buildPaths), size = it.size) },
      reason = data.reason,
    )
  }
}

private fun createPluginKey(buildResult: PluginBuildResult): String {
  val osSuffix = if (buildResult.os == null) "" else " (os=${buildResult.os})"
  val archSuffix = if (buildResult.arch == null) "" else " (arch=${buildResult.arch.name})"
  return buildResult.mainModule + osSuffix + archSuffix
}

private inline fun <reified T : LibraryFileEntry> groupLibraryEntries(
  fileEntries: List<DistributionFileEntry>,
  crossinline getLibraryName: (T) -> String,
): Map<String, List<T>> {
  val entriesGroupedByLibraryName = LinkedHashMap<String, MutableList<T>>()
  for (entry in fileEntries) {
    if (entry is T) {
      entriesGroupedByLibraryName.computeIfAbsent(getLibraryName(entry)) { ArrayList() }.add(entry)
    }
  }
  return entriesGroupedByLibraryName
}

/** The two module lists of one [FileEntry]. */
private class ModuleLists(
  @JvmField val modules: List<ModuleEntry>,
  @JvmField val contentModules: List<ModuleEntry> = emptyList(),
)

/**
 * One [FileEntry] per file of a plugin or a product module.
 *
 * A file of one module library and nothing else states it as [FileEntry.library] and [FileEntry.module]. Every other
 * file takes its modules from [projectModulesBlock]. A file of one project library states it as [FileEntry.library],
 * and a file of several states them as [FileEntry.projectLibraries].
 */
private inline fun projectContentEntries(
  fileToEntry: Map<String, List<DistributionFileEntry>>,
  buildPaths: BuildPaths,
  projectModulesBlock: (List<DistributionFileEntry>) -> ModuleLists,
): List<FileEntry> {
  val result = ArrayList<FileEntry>(fileToEntry.size)
  for ((filePath, fileEntries) in fileToEntry) {
    if (fileEntries.all { it is ModuleLibraryFileEntry }) {
      result.add(projectSeparatePackedModuleLibrary(name = filePath, fileEntries = fileEntries, buildPaths = buildPaths))
      continue
    }

    val modules = projectModulesBlock(fileEntries)
    val projectLibraries = groupProjectLibraries(fileEntries)
    if (projectLibraries.size == 1) {
      val (libraryData, entries) = projectLibraries.entries.first()
      result.add(FileEntry(
        name = filePath,
        modules = modules.modules,
        contentModules = modules.contentModules,
        library = libraryData.libraryName,
        files = entries.map { ModuleLibraryFile(name = libraryFileName(it, buildPaths), size = it.size) },
        reason = libraryData.reason,
      ))
    }
    else {
      result.add(FileEntry(
        name = filePath,
        modules = modules.modules,
        contentModules = modules.contentModules,
        projectLibraries = projectProjectLibraries(grouped = projectLibraries, buildPaths = buildPaths),
      ))
    }
  }
  return result
}
