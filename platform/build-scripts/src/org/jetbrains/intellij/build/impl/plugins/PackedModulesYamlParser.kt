package org.jetbrains.intellij.build.impl.plugins

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.deserializeContentData
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.generateInclusionReasonForContentModule
import org.jetbrains.intellij.build.getLibraryFileName
import org.jetbrains.intellij.build.getLibraryRoots
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Reads the `packed-modules.yaml` that the `ij_plugin` Bazel rule writes for one plugin.
 *
 * The file names each jar of the plugin distribution, and under each jar the modules and libraries the packager put into it. Its
 * shape is the [FileEntry] shape, so that schema's own deserializer reads it. The conversion to [ModuleOutputEntry] adn [ModuleLibraryFileEntry]
 * stays local, because nothing outside this builder wants it.
 *
 * The file names a library by the file name of its JAR. The conversion finds the module-level library of the module that has a JAR
 * with that name, and uses the name and the file of that library.
 */
internal fun readPackedModules(
  packedModulesPath: Path,
  pluginMainModule: String,
  pluginDistributionDirectory: Path,
  outputProvider: ModuleOutputProvider,
): List<DistributionFileEntry> {
  return deserializeContentData(packedModulesPath.readText()).flatMap { entry ->
    checkEntryProperties(packedModulesPath, entry)
    buildList {
      entry.modules.flatMapTo(this) {
        convertModuleEntry(it, entry.name, pluginMainModule, isContentModule = false, pluginDistributionDirectory, outputProvider)
      }
      entry.contentModules.flatMapTo(this) {
        convertModuleEntry(it, entry.name, pluginMainModule, isContentModule = true, pluginDistributionDirectory, outputProvider)
      }
      if (entry.library != null && entry.module != null) {
        add(convertLibraryEntry(entry.library!!, entry.module!!, entry.name, pluginDistributionDirectory, outputProvider))
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
  outputProvider: ModuleOutputProvider,
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
    moduleEntry.libraries.keys.forEach { libraryJarName ->
      val libraryFile = findModuleLibraryFile(moduleItem.moduleName, libraryJarName, outputProvider)
      add(ModuleLibraryFileEntry(
        path = entryPath,
        owner = moduleItem,
        moduleName = moduleItem.moduleName,
        libraryName = libraryFile.libraryName,
        relativeOutputFile = moduleItem.relativeOutputFile,
        libraryFile = libraryFile.file,
        canonicalLibraryPath = null,
        size = 0,
        hash = 0,
      ))
    }
  }
}

private fun convertLibraryEntry(
  libraryJarName: String,
  moduleName: String,
  relativeJarPath: String,
  pluginDistributionDirectory: Path,
  outputProvider: ModuleOutputProvider,
): DistributionFileEntry {
  val moduleItem = ModuleItem(
    moduleName = moduleName,
    relativeOutputFile = relativeJarPath.removePrefix("lib/"),
    reason = null,
  )
  val libraryFile = findModuleLibraryFile(moduleName, libraryJarName, outputProvider)
  return ModuleLibraryFileEntry(
    path = pluginDistributionDirectory.resolve(relativeJarPath),
    owner = moduleItem,
    moduleName = moduleName,
    libraryName = libraryFile.libraryName,
    relativeOutputFile = moduleItem.relativeOutputFile,
    libraryFile = libraryFile.file,
    canonicalLibraryPath = null,
    size = 0,
    hash = 0,
  )
}

private class ModuleLibraryFile(@JvmField val libraryName: String, @JvmField val file: Path)

/**
 * Finds the module-level library of [moduleName] that has a JAR named [libraryJarName].
 */
private fun findModuleLibraryFile(moduleName: String, libraryJarName: String, outputProvider: ModuleOutputProvider): ModuleLibraryFile {
  val module = outputProvider.findRequiredModule(moduleName)
  for (library in module.libraryCollection.libraries) {
    val file = getLibraryRoots(library, outputProvider).find { it.name == libraryJarName } ?: continue
    return ModuleLibraryFile(libraryName = getLibraryFileName(library), file = file)
  }
  error("Cannot find a module-level library with '$libraryJarName' in module '$moduleName'")
}
