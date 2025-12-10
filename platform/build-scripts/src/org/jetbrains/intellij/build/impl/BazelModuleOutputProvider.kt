// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.util.io.toByteArray
import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.jps.model.module.JpsModule
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal class BazelModuleOutputProvider(
  modules: List<JpsModule>,
  private val projectHome: Path,
  val bazelOutputRoot: Path,
  scope: CoroutineScope,
) : ModuleOutputProvider {
  private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }

  // pool of opened ImmutableZipFile instances for efficient O(1) lookups
  private val zipFileCache = AsyncCache<Path, ZipFile?>(scope)

  private suspend fun getZipFile(file: Path): ZipFile? {
    return zipFileCache.getOrPut(file) {
      withContext(Dispatchers.IO) {
        try {
          ImmutableZipFile.load(file)
        }
        catch (e: IOException) {
          if (Files.notExists(file)) {
            return@withContext null
          }
          throw e
        }
      }
    }
  }

  /**
   * Suspend version of [readFileContentFromModuleOutput] using cached [ImmutableZipFile] instances.
   */
  override suspend fun readFileContentFromModuleOutputAsync(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getModuleOutputRootsImpl(module, forTests)) {
      getZipFile(moduleOutput)?.getData(relativePath)?.let { return it }
    }
    return null
  }

  private val bazelTargetsMap: BazelCompilationContext.BazelTargetsInfo.TargetsFile by lazy {
    BazelCompilationContext.BazelTargetsInfo.loadBazelTargetsJson(projectHome)
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

    val paths = library.jars.map { bazelOutputRoot.resolve(it) }

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
    val jarsRelative = if (forTests) moduleDescription.testJars else moduleDescription.productionJars
    val jars = jarsRelative.map { projectHome.resolve(it) }
    return jars
  }

  override suspend fun findFileInAnyModuleOutput(
    relativePath: String,
    moduleNamePrefix: String?,
    processedModules: MutableSet<String>?,
  ): ByteArray? {
    return findFileInAnyModuleOutput(
      modules = nameToModule.values,
      relativePath = relativePath,
      provider = this,
      moduleNamePrefix = moduleNamePrefix,
      processedModules = processedModules,
    )
  }
}

/**
 * Searches for a file across module outputs.
 * If [moduleNamePrefix] is specified, only searches in modules whose name starts with the prefix followed by '.'.
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
    if (moduleNamePrefix != null && !name.startsWith("$moduleNamePrefix.")) {
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