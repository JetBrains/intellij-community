package org.jetbrains.intellij.build.impl.plugins

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.deserializeContentData
import org.jetbrains.intellij.build.generateInclusionReasonForContentModule
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Reads the `packed-modules.yaml` that the `ij_plugin` Bazel rule writes for one plugin.
 *
 * The file names each jar of the plugin distribution, and under each jar the modules and libraries the packager put into it. Its
 * shape is the [FileEntry] shape, so that schema's own deserializer reads it. The conversion to [ModuleOutputEntry] adn [ModuleLibraryFileEntry]
 * stays local, because nothing outside this builder wants it.
 */
internal fun readPackedModules(
  packedModulesPath: Path,
  pluginMainModule: String,
  pluginDistributionDirectory: Path,
): List<DistributionFileEntry> {
  return deserializeContentData(packedModulesPath.readText()).flatMap { entry ->
    checkEntryProperties(packedModulesPath, entry)
    buildList {
      entry.modules.flatMapTo(this) { convertModuleEntry(it, entry.name, pluginMainModule, isContentModule = false, pluginDistributionDirectory) }
      entry.contentModules.flatMapTo(this) { convertModuleEntry(it, entry.name, pluginMainModule, isContentModule = true, pluginDistributionDirectory) }
      if (entry.library != null && entry.module != null) {
        add(convertLibraryEntry(entry.library!!, entry.module!!, entry.name, pluginDistributionDirectory))
      }
    }
  }
}

/**
 * Fails when [entry] carries a field that [readPackedModules] does not convert.
 */
private fun checkEntryProperties(packedModulesPath: Path, entry: FileEntry) {
  check(entry == FileEntry(name = entry.name, modules = entry.modules, contentModules = entry.contentModules, module = entry.module, library = entry.library)) {
    "$packedModulesPath: entry '${entry.name}' sets a field that the build scripts do not convert: $entry"
  }
  for (module in entry.modules.asSequence() + entry.contentModules.asSequence()) {
    check(module == ModuleEntry(name = module.name, libraries = module.libraries)) {
      "$packedModulesPath: module '${module.name}' of '${entry.name}' sets a field that the build scripts do not convert: $module"
    }
  }
}

private fun convertModuleEntry(
  moduleEntry: ModuleEntry,
  relativeJarPath: String,
  pluginMainModule: String,
  isContentModule: Boolean,
  pluginDistributionDirectory: Path,
): List<DistributionFileEntry> {
  val moduleItem = ModuleItem(
    moduleName = moduleEntry.name,
    relativeOutputFile = relativeJarPath.removePrefix("lib/"),
    reason = if (isContentModule) generateInclusionReasonForContentModule(pluginMainModule) else null,
  )
  return buildList {
    val entryPath = pluginDistributionDirectory.resolve(relativeJarPath)
    add(ModuleOutputEntry(
      path = entryPath,
      owner = moduleItem,
      size = 0,
      hash = 0,
      relativeOutputFile = moduleItem.relativeOutputFile,
      reason = moduleItem.reason,
    ))
    moduleEntry.libraries.entries.forEach { libraryEntry ->
      add(ModuleLibraryFileEntry(
        path = entryPath,
        owner = moduleItem,
        moduleName = moduleItem.moduleName,
        libraryName = libraryEntry.key,
        relativeOutputFile = moduleItem.relativeOutputFile,
        libraryFile = null,
        canonicalLibraryPath = null,
        size = 0,
        hash = 0,
      ))
    }
  }
}

private fun convertLibraryEntry(
  libraryName: String,
  moduleName: String,
  relativeJarPath: String,
  pluginDistributionDirectory: Path,
): DistributionFileEntry {
  val moduleItem = ModuleItem(
    moduleName = moduleName,
    relativeOutputFile = relativeJarPath.removePrefix("lib/"),
    reason = null,
  )
  return ModuleLibraryFileEntry(
    path = pluginDistributionDirectory.resolve(relativeJarPath),
    owner = moduleItem,
    moduleName = moduleName,
    libraryName = libraryName,
    relativeOutputFile = moduleItem.relativeOutputFile,
    libraryFile = null,
    canonicalLibraryPath = null,
    size = 0,
    hash = 0,
  )
}
