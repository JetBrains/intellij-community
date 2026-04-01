// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.util.bazelEnvironment.BazelLabel
import com.intellij.util.bazelEnvironment.BazelRunfiles
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@Internal
class BazelModuleOutputProviderState(
  modules: List<JpsModule>,
  @JvmField val projectHome: Path,
  @JvmField val bazelOutputRoot: Path,
  bazelTargetsLoader: (Path) -> BazelTargetsInfo.TargetsFile = BazelTargetsInfo::loadBazelTargetsJson,
) {
  private val index = ModuleOutputProviderIndex(modules)

  val modules: List<JpsModule>
    get() = index.modules

  val bazelTargetsMap: BazelTargetsInfo.TargetsFile by lazy {
    bazelTargetsLoader(projectHome)
  }

  fun findModule(name: String): JpsModule? = index.findModule(name)

  fun findRequiredModule(name: String): JpsModule = index.findRequiredModule(name)

  fun getProjectLibraryToModuleMap(): Map<String, String> = index.getProjectLibraryToModuleMap()

  fun getModuleImlFile(module: JpsModule): Path = index.getModuleImlFile(module)
}

internal class BazelModuleOutputProvider(
  private val state: BazelModuleOutputProviderState,
  scope: CoroutineScope?,
  override val useTestCompilationOutput: Boolean,
) : ModuleOutputProvider {
  constructor(
    modules: List<JpsModule>,
    projectHome: Path,
    bazelOutputRoot: Path,
    scope: CoroutineScope?,
    useTestCompilationOutput: Boolean,
  ) : this(
    state = BazelModuleOutputProviderState(
      modules = modules,
      projectHome = projectHome,
      bazelOutputRoot = bazelOutputRoot,
    ),
    scope = scope,
    useTestCompilationOutput = useTestCompilationOutput,
  )

  private val zipFilePool = ModuleOutputZipFilePool(scope)

  /**
   * Suspend version of [readFileContentFromModuleOutput] using cached zip file instances.
   */
  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getModuleOutputRootsImpl(module, forTests)) {
      zipFilePool.getData(moduleOutput, relativePath)?.let { return it }
    }
    return null
  }

  override fun getAllModules(): List<JpsModule> = state.modules

  override fun findModule(name: String): JpsModule? = state.findModule(name)

  override fun findRequiredModule(name: String): JpsModule = state.findRequiredModule(name)

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    val bazelTargetsMap = state.bazelTargetsMap
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
      library.jars.map { state.bazelOutputRoot.resolve(it) }
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
    val bazelTargetsMap = state.bazelTargetsMap
    val moduleDescription = bazelTargetsMap.modules[module.name] ?: error("Cannot find module '${module.name}' in the project")

    if (forTests && !useTestCompilationOutput) {
      error(
        "Cannot find test sources for module '${module.name}' because 'useTestSourceEnabled' is false.\n" +
        "System property '${BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY}' value: ${System.getProperty(BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY)}, " +
        "BazelModuleOutputProvider.useTestCompilationOutput (from BuildOptions.useTestCompilationOutput) value: $useTestCompilationOutput, " +
        "default value: ${BuildOptions.USE_TEST_COMPILATION_OUTPUT_DEFAULT_VALUE}"
      )
    }

    return if (BazelRunfiles.isRunningFromBazel) {
      val targets = if (forTests) moduleDescription.testTargets else moduleDescription.productionTargets
      targets.map { BazelRunfiles.getFileByLabel(BazelLabel.fromString(it)) }
    }
    else {
      val jarsRelative = if (forTests) moduleDescription.testJars else moduleDescription.productionJars
      jarsRelative.map { state.projectHome.resolve(it) }
    }
  }

  override suspend fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
    return findFileInAnyModuleOutput(
      modules = state.modules,
      relativePath = relativePath,
      provider = this,
      moduleNamePrefix = moduleNamePrefix,
      processedModules = processedModules,
    )
  }

  override fun getProjectLibraryToModuleMap(): Map<String, String> = state.getProjectLibraryToModuleMap()

  override fun getModuleImlFile(module: JpsModule): Path = state.getModuleImlFile(module)

  override fun toString(): String = "BazelModuleOutputProvider(projectHome=${state.projectHome}, bazelOutputRoot=${state.bazelOutputRoot})"
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
    provider.readFileContentFromModuleOutput(module = module, relativePath = relativePath, forTests = false)?.let {
      return it
    }
  }
  return null
}
