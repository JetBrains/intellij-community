// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.util.io.toByteArray
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.bazelEnvironment.BazelLabel
import org.jetbrains.intellij.bazelEnvironment.BazelRunfiles
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal class BazelModuleOutputProvider(
  modules: List<JpsModule>,
  private val projectHome: Path,
  val bazelOutputRoot: Path,
  scope: CoroutineScope?,
) : ModuleOutputProvider {
  private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }

  private val zipFilePool = ModuleOutputZipFilePool(scope)

  /**
   * Suspend version of [readFileContentFromModuleOutput] using cached zip file instances.
   */
  override suspend fun readFileContentFromModuleOutputAsync(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getModuleOutputRootsImpl(module, forTests)) {
      zipFilePool.getData(moduleOutput, relativePath)?.let { return it }
    }
    return null
  }

  private val bazelTargetsMap: BazelTargetsInfo.TargetsFile by lazy {
    BazelTargetsInfo.loadBazelTargetsJson(projectHome)
  }

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val result = getModuleOutputRootsImpl(module, forTests).mapNotNull { moduleOutput ->
      if (Files.notExists(moduleOutput)) {
        return@mapNotNull null
      }
      var fileContent: ByteArray? = null
      readZipFile(moduleOutput) { name, data ->
        if (name == relativePath) {
          fileContent = data().toByteArray()
          ZipEntryProcessorResult.STOP
        }
        else {
          ZipEntryProcessorResult.CONTINUE
        }
      }
      fileContent
    }
    check(result.size < 2) {
      "More than one '$relativePath' file for module '${module.name}' in output roots"
    }
    return result.singleOrNull()
  }

  override fun findModule(name: String): JpsModule? = nameToModule.get(name.removeSuffix("._test"))

  override fun findRequiredModule(name: String): JpsModule {
    return requireNotNull(findModule(name)) {
      "Cannot find required module '$name' in the project"
    }
  }

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    val librariesTable = if (moduleLibraryModuleName == null) {
      bazelTargetsMap.projectLibraries
    }
    else {
      val module = bazelTargetsMap.modules[moduleLibraryModuleName] ?: error("Cannot find module '$moduleLibraryModuleName' in the project")
      module.moduleLibraries
    }

    val libraryMoniker = "library '$libraryName' " +
                         if (moduleLibraryModuleName == null) "(project level)" else "(in module '$moduleLibraryModuleName'"
    val library = librariesTable[libraryName] ?: error(
      "Cannot find $libraryMoniker"
    )

    val paths = if (BazelRunfiles.isRunningFromBazel) {
      library.jarTargets.map { BazelRunfiles.getFileByLabel(BazelLabel.fromString(it)) }
    }
    else {
      library.jars.map { bazelOutputRoot.resolve(it) }
    }

    check(paths.isNotEmpty()) {
      "No files found for $libraryMoniker"
    }

    for (path in paths) {
      check(path.isRegularFile()) {
        "Library file '$path' does not exists, required for $libraryMoniker. Locally please run ./bazel-build-all.cmd"
      }
    }

    return paths
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val jars = getModuleOutputRootsImpl(module, forTests)
    for (path in jars) {
      check(path.isRegularFile()) {
        "Module output '$path' does not exists, required for module ${module.name}. Locally please run ./bazel-build-all.cmd"
      }
    }
    return jars
  }

  private fun getModuleOutputRootsImpl(module: JpsModule, forTests: Boolean): List<Path> {
    val moduleDescription = bazelTargetsMap.modules[module.name] ?: error("Cannot find module '${module.name}' in the project")

    return if (BazelRunfiles.isRunningFromBazel) {
      val targets = if (forTests) moduleDescription.testTargets else moduleDescription.productionTargets
      targets.map { BazelRunfiles.getFileByLabel(BazelLabel.fromString(it)) }
    }
    else {
      val jarsRelative = if (forTests) moduleDescription.testJars else moduleDescription.productionJars
      jarsRelative.map { projectHome.resolve(it) }
    }
  }

  override suspend fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
    return findFileInAnyModuleOutput(
      modules = nameToModule.values,
      relativePath = relativePath,
      provider = this,
      moduleNamePrefix = moduleNamePrefix,
      processedModules = processedModules,
    )
  }

  override fun getModuleImlFile(module: JpsModule): Path {
    val baseDir = requireNotNull(JpsModelSerializationDataService.getBaseDirectoryPath(module)) {
      "Cannot find base directory for module ${module.name}"
    }
    return baseDir.resolve("${module.name}.iml")
  }

  override fun toString(): String = "BazelModuleOutputProvider(projectHome=$projectHome, bazelOutputRoot=$bazelOutputRoot)"
}

/**
 * Searches for a file across module outputs.
 * If [moduleNamePrefix] is specified, only searches in modules whose name starts with the prefix.
 * If [processedModules] is specified, skips modules already in the set and adds searched modules to it.
 */
internal suspend fun findFileInAnyModuleOutput(
  modules: Iterable<JpsModule>,
  relativePath: String,
  provider: ModuleOutputProvider,
  moduleNamePrefix: String? = null,
  processedModules: MutableSet<String>? = null,
): ByteArray? {
  for (module in modules) {
    val name = module.name
    if (moduleNamePrefix != null && !name.startsWith(moduleNamePrefix)) {
      continue
    }
    if (processedModules != null && !processedModules.add(name)) {
      continue
    }
    provider.readFileContentFromModuleOutputAsync(module = module, relativePath = relativePath, forTests = false)?.let {
      return it
    }
  }
  return null
}