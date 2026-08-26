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
import kotlin.io.path.invariantSeparatorsPathString
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

  /**
   * The path of [label], or `null` when an explicit manifest is configured and does not declare it.
   *
   * For a **probe** - a descriptor search asking many candidates for one file, at most one of which has it - and for
   * nothing else. Such a search is defined by tolerating a miss, and under an explicit manifest a jar the fragment does
   * not declare is a jar whose bytes may not reach this fragment's output, so "not declared" is a miss of exactly that
   * kind. Resolving it strictly instead is what forced a fragment to declare every candidate a search might touch: the
   * Kotlin plugin's descriptor search asked 20 platform modules for a file that was in a library all along, and one
   * plugin fragment then held the whole platform's hot jars as action inputs.
   *
   * This does not soften under-declaration into silence. A search that finds its file nowhere still throws - see
   * `XIncludeElementResolverImpl.resolveElement`, "Cannot resolve '<path>' in <scopes>" - and a read that *packs*
   * bytes, every caller in `JarPackager`, goes through [resolve] and still fails on an undeclared input.
   */
  fun resolveIfDeclared(label: String): Path? {
    val resolver = resolver ?: return BazelRunfiles.getFileByLabel(BazelLabel.fromString(label))
    return resolver.resolveIfDeclared(label)
  }

  /**
   * Every file [label] declares, in manifest order, with [resolveIfDeclared]'s probe contract: `null` when an explicit
   * manifest does not declare [label], and `null` when no manifest is configured at all.
   *
   * For a **library container** - the `jvm_import`/`java_library`/`java_import` that groups a library's jars, recorded
   * as `LibraryDescription.target`. A multi-jar library resolves to several files and the order is the packer's
   * duplicate-resolution order, so it must not be sorted. Use [resolve] where exactly one file is the contract, such as
   * a module target.
   *
   * **Manifest only, deliberately with no runfiles fallback.** A container target is a `java_library`/`jvm_import`, not
   * a file, so `BazelRunfiles.getFileByLabel` cannot resolve it - it fails with "Unable to find dependency
   * '@lib//:kotlin-stdlib'". Only the input manifest maps a container key to files, because
   * `intellij_dev_build_inputs` is what expands it. A caller that may run under plain runfiles has to take the per-jar
   * `LibraryDescription.jarTargets` branch instead, which is one of the reasons that field stays in `bazel-targets.json`.
   */
  fun resolveAllIfDeclared(label: String): List<Path>? = resolver?.resolveAllIfDeclared(label)

  /**
   * The manifest key [file] was declared under, or `null` when no manifest is configured or none declares that file.
   *
   * The inverse of [resolve], for naming a file the build has already read rather than for finding one: the executed
   * packaging recipe identifies each source by its key, because a key is the same on every machine and an execution
   * path is not. It deliberately does **not** mark the input as used - a name is not a read, and counting it as one
   * would make merely reporting a fragment's recipe shrink its unused-input list.
   */
  fun labelOf(file: Path): String? = resolver?.labelOf(file)

  /**
   * Which file of [label] [file] is, or `null` where no one name answers that.
   *
   * `RecipeSource.file` states the rule and what a reader does with an absent value. This function returns `null` in
   * four cases: no manifest is configured, [label] declares one file, [label] does not declare [file], and no name
   * separates [file] from a sibling of the same container.
   *
   * For **naming**, beside [labelOf], and never for reading bytes. It marks no input as used. That is [labelOf]'s
   * reason: a name is not a read. Counting one would make merely reporting a recipe shrink a fragment's unused-input
   * list. [resolveAllIfDeclared] is what reads a container's files.
   */
  fun declaredFileNameOf(label: String, file: Path): String? = resolver?.declaredFileNameOf(label = label, file = file)

  fun writeUnusedInputs(file: Path) {
    resolver?.writeUnusedInputs(file) ?: Files.writeString(file, "")
  }
}

private data class ExplicitBazelInput(
  @JvmField val execPath: String,
  @JvmField val absolutePath: Path,
)

/**
 * Resolves the labels a fragment declared, and records which of them it read.
 *
 * A key maps to an ordered *list* of files, not to one file. A module target and a raw input are one jar each, but a
 * library is keyed by the container target that groups its jars, so a multi-jar library resolves to several - in the
 * order the manifest lists them, which is the order the container's `exports` declare and which the packer depends on
 * for duplicate entries. The manifest keeps one line per file and repeats the key, so the key's files are exactly its
 * lines in order.
 */
internal class ExplicitBazelInputResolver private constructor(
  private val inputs: Map<String, List<ExplicitBazelInput>>,
  /**
   * The key each declared file is named by, for [labelOf].
   *
   * Built once at load time and never written afterwards, which is why it needs none of this class's synchronization:
   * it is the only member that is not part of the used-input bookkeeping.
   */
  private val labelByPath: Map<Path, String>,
) {
  private val usedExecPaths = HashSet<String>()

  @Synchronized
  fun resolve(label: String): Path = resolveAll(label).single()

  @Synchronized
  fun resolveAll(label: String): List<Path> {
    val declared = inputs.get(label) ?: error("Bazel input '$label' is not declared in the explicit input manifest")
    return declared.map {
      usedExecPaths.add(it.execPath)
      it.absolutePath
    }
  }

  @Synchronized
  fun resolveIfDeclared(label: String): Path? = resolveAllIfDeclared(label)?.single()

  @Synchronized
  fun resolveAllIfDeclared(label: String): List<Path>? {
    val declared = inputs.get(label) ?: return null
    return declared.map {
      usedExecPaths.add(it.execPath)
      it.absolutePath
    }
  }

  fun labelOf(file: Path): String? = labelByPath.get(file)

  /**
   * Which file of [label] [file] is, by [discriminateExecPath]'s rule.
   *
   * It works on the **execution paths** the manifest wrote, and not on the absolute paths. Every declared file shares
   * the execution root, so an absolute path lets the search widen above that root. The value would then hold a name
   * element of this machine, in a report that keys on labels to avoid exactly that.
   *
   * Needs no synchronization, for [labelOf]'s reason: [inputs] is built at load time and never written afterwards.
   */
  fun declaredFileNameOf(label: String, file: Path): String? {
    val declared = inputs.get(label) ?: return null
    if (declared.size < 2) {
      return null
    }
    // A file the key does not declare must get no name. Otherwise the search can hand it a sibling's name.
    val own = declared.firstOrNull { it.absolutePath == file } ?: return null
    return discriminateExecPath(declared = declared.map { Path.of(it.execPath) }, execPath = Path.of(own.execPath))
  }

  @Synchronized
  fun writeUnusedInputs(file: Path) {
    file.parent?.let { Files.createDirectories(it) }
    val unused = inputs.values.asSequence().flatten().map(ExplicitBazelInput::execPath).filterNot(usedExecPaths::contains).distinct().sorted().toList()
    // Empty means an empty file, not a lone newline. The only reader is `wc -l`
    // (`build/dev_dist_unused_inputs_test.bzl`), so a trailing newline with nothing before it reports a fully honest
    // fragment as having one unused input.
    Files.writeString(file, if (unused.isEmpty()) "" else unused.joinToString(separator = "\n", postfix = "\n"))
  }

  companion object {
    fun load(file: Path): ExplicitBazelInputResolver {
      val inputs = LinkedHashMap<String, MutableList<ExplicitBazelInput>>()
      val labelByPath = HashMap<Path, String>()
      Files.readAllLines(file).forEachIndexed { index, line ->
        if (line.isBlank()) return@forEachIndexed
        val separator = line.indexOf('\t')
        check(separator > 0 && separator < line.lastIndex) { "Malformed Bazel input manifest line ${index + 1} in $file" }
        val label = line.substring(0, separator)
        val execPath = line.substring(separator + 1)
        check(!Path.of(execPath).isAbsolute) { "Bazel input path '$execPath' must be relative to the execution root" }
        val input = ExplicitBazelInput(execPath = execPath, absolutePath = Path.of(execPath).toAbsolutePath().normalize())
        // The label as the manifest writes it, not the apparent-repository alias synthesized below: a report has to
        // name one key per file, and the written one is the key the generator emitted. First wins, so a file declared
        // under two keys - a module target and the library container that exports it - is named by the first.
        labelByPath.putIfAbsent(input.absolutePath, label)
        // A repeated label is how a multi-jar library states its jars, so appending is the normal case and order is
        // preserved. What is still a defect is the *same* file twice under one key: the writer deduplicates
        // (`_collect_libraries`, first-wins), so a repeat here means two producers disagreed about the same key.
        for (key in listOfNotNull(label, apparentRepositoryLabel(label))) {
          val declared = inputs.getOrPut(key) { mutableListOf() }
          check(declared.none { it.execPath == execPath }) { "Duplicate Bazel input '$key' -> '$execPath' in $file" }
          declared.add(input)
        }
      }
      return ExplicitBazelInputResolver(inputs = inputs, labelByPath = labelByPath)
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

/**
 * The shortest trailing path of [execPath] that exactly one of [declared] ends with, or `null` when no width has one.
 *
 * The search starts at the file name and widens by one name each round. The first width that names one file wins, so the
 * value is the shortest one that separates [execPath] from its siblings. Every candidate is a trailing path of an
 * execution path, so it means the same thing on another machine.
 *
 * The search can end with nothing. Two files of one key can shadow each other, as `a/b.jar` and `x/a/b.jar` do. No width
 * of the shorter path then separates the two. `RecipeSource.file` states what a reader does with that.
 */
private fun discriminateExecPath(declared: List<Path>, execPath: Path): String? {
  for (names in 1..execPath.nameCount) {
    val candidate = execPath.subpath(execPath.nameCount - names, execPath.nameCount)
    if (declared.count { it.endsWith(candidate) } == 1) {
      return candidate.invariantSeparatorsPathString
    }
  }
  return null
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
   *
   * A probe by contract - it returns `null` for a module that does not have the file - so it reads only the module
   * outputs this build declares; see [BazelBuildInputs.resolveIfDeclared].
   */
  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getModuleOutputRootsImpl(module, forTests, declaredOnly = true)) {
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

  override fun findDeclaredLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    if (!BazelBuildInputs.isConfigured && !BazelRunfiles.isRunningFromBazel) {
      // No manifest, so nothing to narrow to, and a library this build cannot find is still an error worth reporting.
      return findLibraryRoots(libraryName = libraryName, moduleLibraryModuleName = moduleLibraryModuleName)
    }

    val library = (libraryDescriptions(moduleLibraryModuleName) ?: return emptyList())[libraryName] ?: return emptyList()
    // Under a manifest the key is the container, with the test-plugin fallback [resolveDeclaredLibrary] explains; under
    // plain runfiles it has to be the per-jar labels, because a container target is not a file - see
    // `BazelBuildInputs.resolveAllIfDeclared`.
    //
    // Deliberately *not* [resolveDeclaredLibrary], which the strict path uses: this is a probe, so a partly declared
    // library yields the jars that are declared rather than nothing. The file being searched for may be in one of them,
    // and an undeclared jar has to answer like a jar without the file.
    val paths = if (BazelBuildInputs.isConfigured) {
      BazelBuildInputs.resolveAllIfDeclared(library.target)
      ?: library.jarTargets.mapNotNull(BazelBuildInputs::resolveIfDeclared)
    }
    else {
      library.jarTargets.map { BazelRunfiles.getFileByLabel(BazelLabel.fromString(it)) }
    }
    return paths.filter { it.isRegularFile() }
  }

  /**
   * The `bazel-targets.json` library table [moduleLibraryModuleName] names, or `null` when the project has no such
   * module. A `null` module name asks for the project-level table, which always exists.
   */
  private fun libraryDescriptions(moduleLibraryModuleName: String?): Map<String, BazelTargetsInfo.LibraryDescription>? {
    val bazelTargetsMap = state.bazelTargetsMap
    if (moduleLibraryModuleName == null) {
      return bazelTargetsMap.projectLibraries
    }
    return bazelTargetsMap.modules[moduleLibraryModuleName]?.moduleLibraries
  }

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    val librariesTable = libraryDescriptions(moduleLibraryModuleName)
                         ?: error("Cannot find module '$moduleLibraryModuleName' in the project")

    val libraryMoniker = "library '$libraryName' " +
                         if (moduleLibraryModuleName == null) "(project level)" else "(in module '$moduleLibraryModuleName')"
    val library = librariesTable[libraryName] ?: error(
      "Cannot find $libraryMoniker"
    )

    // Three sources, and the middle one is why `jarTargets` stays in `bazel-targets.json`. Under a fragment's explicit
    // manifest the key is the library *container*, whose label carries no artifact version and which the manifest
    // expands back into ordered jars. Under plain Bazel runfiles there is no manifest to expand anything, and a
    // container target is not a file, so the per-jar labels are the only thing resolvable - the same branch
    // `ArchivedCompilationContextUtil` and `MonorepoProjectStructure` take. Outside Bazel entirely it is the output root.
    val paths = when {
      BazelBuildInputs.isConfigured -> resolveDeclaredLibrary(library, libraryMoniker)
      BazelRunfiles.isRunningFromBazel -> library.jarTargets.map { BazelRunfiles.getFileByLabel(BazelLabel.fromString(it)) }
      else -> library.jars.map { state.bazelOutputRoot.resolve(it) }
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

  /**
   * A declared library's files, by the container key or - for the one producer that cannot write one - its jar keys.
   *
   * Every declaration a *generator* writes keys a library by its container target
   * (`computeLibraryContainerLabels`, `addMemberLibraries`), because that label carries no artifact version and so
   * stays out of a Maven bump's diff. Test plugins are the exception, as they are for content generally: they have no
   * `plugin-content.yaml`, so the dynamic JPS-to-Bazel bridge derives their payload from library XML while loading, and
   * what a library XML yields is jar file labels - a container's target name comes from the library's *name* through the
   * branchy derivation in `dependency.kt:130-290`, which is not worth mirroring in Starlark for a payload that is
   * checked into nothing and therefore causes no churn either way.
   *
   * So each producer has exactly one convention and this picks between them, container first. Under-declaration stays
   * loud: neither key declared is an error naming the library.
   */
  private fun resolveDeclaredLibrary(library: BazelTargetsInfo.LibraryDescription, libraryMoniker: String): List<Path> {
    BazelBuildInputs.resolveAllIfDeclared(library.target)?.let { return it }
    val perJar = library.jarTargets.mapNotNull(BazelBuildInputs::resolveIfDeclared)
    check(perJar.size == library.jarTargets.size) {
      "Neither the container '${library.target}' nor every jar of $libraryMoniker is declared in the explicit input manifest"
    }
    return perJar
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

  private fun getModuleOutputRootsImpl(module: JpsModule, forTests: Boolean, declaredOnly: Boolean = false): List<Path> {
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
      if (declaredOnly) targets.mapNotNull(BazelBuildInputs::resolveIfDeclared) else targets.map(BazelBuildInputs::resolve)
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
