// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

private const val BAZEL_BUILD_INPUTS_MANIFEST_PROPERTY = "intellij.build.bazel.inputs.manifest"

@Internal
object BazelBuildInputs {
  val isConfigured: Boolean
    get() = System.getProperty(BAZEL_BUILD_INPUTS_MANIFEST_PROPERTY) != null

  private val resolver: ExplicitBazelInputResolver? by lazy {
    System.getProperty(BAZEL_BUILD_INPUTS_MANIFEST_PROPERTY)?.let { ExplicitBazelInputResolver.load(Path.of(it)) }
  }

  fun resolve(label: String): Path {
    return resolver?.resolve(label) ?: BazelRunfiles.getFileByLabel(BazelLabel.fromString(label))
  }

  fun writeUnusedInputs(file: Path) {
    resolver?.writeUnusedInputs(file) ?: Files.writeString(file, "")
  }
}

private data class ExplicitBazelInput(
  @JvmField val execPath: String,
  @JvmField val absolutePath: Path,
)

internal class ExplicitBazelInputResolver private constructor(
  private val inputs: Map<String, ExplicitBazelInput>,
) {
  private val usedExecPaths = HashSet<String>()

  @Synchronized
  fun resolve(label: String): Path {
    val input = inputs.get(label) ?: error("Bazel input '$label' is not declared in the explicit input manifest")
    usedExecPaths.add(input.execPath)
    return input.absolutePath
  }

  @Synchronized
  fun writeUnusedInputs(file: Path) {
    file.parent?.let { Files.createDirectories(it) }
    val unused = inputs.values.asSequence().map(ExplicitBazelInput::execPath).filterNot(usedExecPaths::contains).distinct().sorted()
    Files.writeString(file, unused.joinToString(separator = "\n", postfix = "\n"))
  }

  companion object {
    fun load(file: Path): ExplicitBazelInputResolver {
      val inputs = LinkedHashMap<String, ExplicitBazelInput>()
      Files.readAllLines(file).forEachIndexed { index, line ->
        if (line.isBlank()) return@forEachIndexed
        val separator = line.indexOf('\t')
        check(separator > 0 && separator < line.lastIndex) { "Malformed Bazel input manifest line ${index + 1} in $file" }
        val label = line.substring(0, separator)
        val execPath = line.substring(separator + 1)
        check(!Path.of(execPath).isAbsolute) { "Bazel input path '$execPath' must be relative to the execution root" }
        val input = ExplicitBazelInput(execPath = execPath, absolutePath = Path.of(execPath).toAbsolutePath().normalize())
        check(inputs.put(label, input) == null) { "Duplicate Bazel input label '$label' in $file" }
        apparentRepositoryLabel(label)?.let { apparentLabel ->
          check(inputs.put(apparentLabel, input) == null) { "Duplicate Bazel input label '$apparentLabel' in $file" }
        }
      }
      return ExplicitBazelInputResolver(inputs)
    }

    private fun apparentRepositoryLabel(label: String): String? {
      if (!label.startsWith("@@")) return null
      val repositoryEnd = label.indexOf("//")
      if (repositoryEnd == -1) return null
      val canonicalRepository = label.substring(2, repositoryEnd)
      val apparentRepository = canonicalRepository.substringBefore('+')
      return if (apparentRepository.isEmpty()) label.substring(repositoryEnd) else "@$apparentRepository${label.substring(repositoryEnd)}"
    }
  }
}

@Internal
class BazelModuleOutputProviderState(
  modules: List<JpsModule>,
  @JvmField val projectHome: Path,
  bazelOutputRootResolver: () -> Path = {
    requireNotNull(bazelOutputRoot) { "Bazel output root is not available" }
  },
  bazelTargetsLoader: (Path) -> BazelTargetsInfo.TargetsFile = BazelTargetsInfo::loadBazelTargetsJson,
) {
  private val index = ModuleOutputProviderIndex(modules)

  /**
   * Demanded only to locate library jars outside explicit Bazel inputs and runfiles, where every path comes from a
   * label. Resolving it lazily lets a build whose own jars were copied out of `bazel-out` use those inputs without
   * inventing another way to derive the output root.
   */
  private val lazyBazelOutputRoot = lazy { bazelOutputRootResolver() }

  val bazelOutputRoot: Path
    get() = lazyBazelOutputRoot.value

  /** For diagnostics only - reporting must not be what forces [bazelOutputRoot] to resolve. */
  internal val resolvedBazelOutputRoot: Path?
    get() = if (lazyBazelOutputRoot.isInitialized()) lazyBazelOutputRoot.value else null

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
  private val testCompilationOutputModules: Set<String> = emptySet(),
) : ModuleOutputProvider {
  constructor(
    modules: List<JpsModule>,
    projectHome: Path,
    bazelOutputRoot: Path,
    scope: CoroutineScope?,
    useTestCompilationOutput: Boolean,
    testCompilationOutputModules: Set<String> = emptySet(),
  ) : this(
    state = BazelModuleOutputProviderState(
      modules = modules,
      projectHome = projectHome,
      bazelOutputRootResolver = { bazelOutputRoot },
    ),
    scope = scope,
    useTestCompilationOutput = useTestCompilationOutput,
    testCompilationOutputModules = testCompilationOutputModules,
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

  override fun isTestCompilationOutputEnabled(module: JpsModule): Boolean {
    return useTestCompilationOutput || testCompilationOutputModules.contains(module.name)
  }

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

    val paths = if (BazelBuildInputs.isConfigured || BazelRunfiles.isRunningFromBazel) {
      library.jarTargets.map(BazelBuildInputs::resolve)
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

  fun findPluginDistributionTargetDescription(mainModuleName: String) : BazelTargetsInfo.PluginDistributionTargetDescription? {
    return state.bazelTargetsMap.pluginDistributionTargets[mainModuleName]
  }

  private fun getModuleOutputRootsImpl(module: JpsModule, forTests: Boolean): List<Path> {
    val bazelTargetsMap = state.bazelTargetsMap
    val moduleDescription = bazelTargetsMap.modules[module.name] ?: error("Cannot find module '${module.name}' in the project")

    if (forTests && !isTestCompilationOutputEnabled(module)) {
      error(
        "Cannot find test sources for module '${module.name}' because 'useTestSourceEnabled' is false.\n" +
        "System property '${BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY}' value: ${System.getProperty(BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY)}, " +
        "selective modules: $testCompilationOutputModules, " +
        "BazelModuleOutputProvider.useTestCompilationOutput (from BuildOptions.useTestCompilationOutput) value: $useTestCompilationOutput, " +
        "default value: ${BuildOptions.USE_TEST_COMPILATION_OUTPUT_DEFAULT_VALUE}"
      )
    }

    return if (BazelBuildInputs.isConfigured || BazelRunfiles.isRunningFromBazel) {
      val targets = if (forTests) moduleDescription.testTargets else moduleDescription.productionTargets
      targets.map(BazelBuildInputs::resolve)
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

  override fun toString(): String = "BazelModuleOutputProvider(projectHome=${state.projectHome}, bazelOutputRoot=${state.resolvedBazelOutputRoot ?: "<not resolved>"})"
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
