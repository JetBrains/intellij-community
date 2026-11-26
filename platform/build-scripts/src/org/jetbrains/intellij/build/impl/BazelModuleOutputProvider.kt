// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.platform.bazel.runfiles.BazelRunfilesManifest
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.buildSpan
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.jps.model.module.JpsModule
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.BitSet
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile

/** Resolves a Bazel label to its file in the runfiles of `bazel run` or `bazel test`. */
@Internal
object BazelBuildInputs {
  /** Whether [resolve] can read a label: in the runfiles of `bazel run` or `bazel test`. */
  val canResolveLabels: Boolean
    get() = BazelRunfiles.isRunningFromBazel

  fun resolve(label: String): Path = BazelRunfiles.getFileByLabel(BazelLabel.fromString(label))
}

@Internal
class BazelModuleOutputProviderState(
  modules: List<JpsModule>,
  @JvmField val projectHome: Path,
  bazelOutputRootResolver: () -> Path = {
    requireNotNull(bazelOutputRoot) { "Bazel output root is not available" }
  },
  bazelTargetsLoader: (Path) -> BazelTargetsInfo.TargetsFile = BazelTargetsInfo::loadBazelTargetsJson,
  /**
   * `null` takes [BazelOutputMode.current]. A test sets it to choose the branch and the error message of one mode in
   * any runner. The resolution of a label still reads the runfiles of the process through [BazelRunfiles].
   */
  private val outputMode: BazelOutputMode? = null,
) {
  private val index = ModuleOutputProviderIndex(modules)

  val mode: BazelOutputMode
    get() = outputMode ?: BazelOutputMode.current()

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

  fun findFileInModuleSources(module: JpsModule, relativePath: String, onlyProductionSources: Boolean): Path? {
    return index.sourceFiles.find(module = module, relativePath = relativePath, onlyProductionSources = onlyProductionSources)
  }

  fun findModulesWithSourceFile(relativePath: String): List<JpsModule> = index.sourceFiles.findOwners(relativePath)

  fun getModuleImlFile(module: JpsModule): Path = index.getModuleImlFile(module)
}

@Internal
class BazelModuleOutputProvider(
  private val state: BazelModuleOutputProviderState,
  lifetime: BuildLifetime?,
  override val useTestCompilationOutput: Boolean,
  private val testCompilationOutputModules: Set<String> = emptySet(),
) : ModuleOutputProvider {
  constructor(
    modules: List<JpsModule>,
    projectHome: Path,
    bazelOutputRoot: Path,
    lifetime: BuildLifetime?,
    useTestCompilationOutput: Boolean,
    testCompilationOutputModules: Set<String> = emptySet(),
  ) : this(
    state = BazelModuleOutputProviderState(
      modules = modules,
      projectHome = projectHome,
      bazelOutputRootResolver = { bazelOutputRoot },
    ),
    lifetime = lifetime,
    useTestCompilationOutput = useTestCompilationOutput,
    testCompilationOutputModules = testCompilationOutputModules,
  )

  private val zipFilePool = ModuleOutputZipFilePool(lifetime)

  /**
   * The probe roots of a module, by module name and by the output kind. A probe over the whole project asks every
   * module once per path, and the resolution of a label to a file costs a file system call, so the answer is kept.
   */
  private val probeRoots = ConcurrentHashMap<String, List<Path>>()

  /**
   * Reads through the pool of cached zip file instances.
   *
   * A probe by contract: it returns `null` for a module that does not have the file. It reads only the roots that
   * [computeProbeRoots] gives.
   */
  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getProbeRoots(module, forTests)) {
      zipFilePool.getData(moduleOutput, relativePath)?.let { return it }
    }
    return null
  }

  private fun getProbeRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val key = if (forTests) module.name + ":test" else module.name
    return probeRoots.get(key) ?: computeProbeRoots(module, forTests).also {
      probeRoots.putIfAbsent(key, it)
    }
  }

  /**
   * The index of the production outputs of every module for [findFileInAnyModuleOutput].
   * The first search builds it, and the other searches wait for it.
   */
  private val outputEntryIndex by lazy {
    ModuleOutputEntryIndex.build(modules = state.modules) { module ->
      val names = ArrayList<String>()
      for (root in getProbeRoots(module, forTests = false)) {
        names.addAll(zipFilePool.readEntryNames(root, ModuleOutputEntryIndex::isIndexedName) ?: return@build null)
      }
      names
    }
  }

  override fun getAllModules(): List<JpsModule> = state.modules

  override fun isTestCompilationOutputEnabled(module: JpsModule): Boolean {
    return useTestCompilationOutput || testCompilationOutputModules.contains(module.name)
  }

  override fun findModule(name: String): JpsModule? = state.findModule(name)

  override fun findRequiredModule(name: String): JpsModule = state.findRequiredModule(name)

  override fun findFileInModuleSources(module: JpsModule, relativePath: String, onlyProductionSources: Boolean): Path? {
    return state.findFileInModuleSources(module = module, relativePath = relativePath, onlyProductionSources = onlyProductionSources)
  }

  override fun findModulesWithSourceFile(relativePath: String): List<JpsModule> = state.findModulesWithSourceFile(relativePath)

  override fun findDeclaredLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    if (state.mode == BazelOutputMode.OUTSIDE_BAZEL) {
      // No runfiles, so nothing to narrow to, and a library this build cannot find is still an error worth reporting.
      return findLibraryRoots(libraryName = libraryName, moduleLibraryModuleName = moduleLibraryModuleName)
    }

    val library = (libraryDescriptions(moduleLibraryModuleName) ?: return emptyList())[libraryName] ?: return emptyList()
    // Under runfiles the library resolves by its per-jar labels, because a container target is not a file. This is a
    // probe, so a jar that the runfiles lack answers like a jar without the file.
    return library.jarTargets.mapNotNull(::findRunfileByLabel).filter { it.isRegularFile() }
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

  /**
   * The per-jar labels of `bazel-targets.json`. Two libraries that name one artifact name one label for it, whichever
   * container groups them, and the table needs no runfiles and no file. `jars` would also do, but a label is what
   * every other reader of the table keys a jar by.
   */
  override fun getLibraryJarIdentities(libraryName: String, moduleLibraryModuleName: String?): List<String> {
    return findLibraryDescription(libraryName = libraryName, moduleLibraryModuleName = moduleLibraryModuleName).jarTargets
  }

  private fun findLibraryDescription(libraryName: String, moduleLibraryModuleName: String?): BazelTargetsInfo.LibraryDescription {
    val librariesTable = libraryDescriptions(moduleLibraryModuleName)
                         ?: error("Cannot find module '$moduleLibraryModuleName' in the project")
    return librariesTable[libraryName] ?: error("Cannot find ${libraryMoniker(libraryName, moduleLibraryModuleName)}")
  }

  private fun libraryMoniker(libraryName: String, moduleLibraryModuleName: String?): String {
    return "library '$libraryName' " + if (moduleLibraryModuleName == null) "(project level)" else "(in module '$moduleLibraryModuleName')"
  }

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    val missing = MissingBazelOutputs(state.mode)
    val paths = collectLibraryRoots(libraryName = libraryName, moduleLibraryModuleName = moduleLibraryModuleName, missing = missing)
    missing.throwIfAny(subject = libraryMoniker(libraryName, moduleLibraryModuleName))
    return paths
  }

  /**
   * The roots of a library, as [findLibraryRoots] gives them. An absent jar goes into [missing], and the caller reports
   * all absent jars of its request at once.
   */
  internal fun collectLibraryRoots(libraryName: String, moduleLibraryModuleName: String?, missing: MissingBazelOutputs): List<Path> {
    val libraryMoniker = libraryMoniker(libraryName, moduleLibraryModuleName)
    val library = findLibraryDescription(libraryName = libraryName, moduleLibraryModuleName = moduleLibraryModuleName)

    // Under Bazel runfiles a container target is not a file, so the library resolves jar by jar, and that is why
    // `jarTargets` stays in `bazel-targets.json`. `ArchivedCompilationContextUtil` and `MonorepoProjectStructure` also
    // take the per-jar labels. Outside Bazel it is the output root.
    val jars = when (state.mode) {
      BazelOutputMode.RUNFILES -> resolveLibraryRunfiles(library, libraryMoniker)
      BazelOutputMode.OUTSIDE_BAZEL -> library.jars.mapIndexed { index, jar ->
        BazelOutputJar(label = library.jarTargets.getOrNull(index) ?: library.target, file = state.bazelOutputRoot.resolve(jar))
      }
    }

    check(jars.isNotEmpty()) {
      "No files found for $libraryMoniker"
    }
    return jars.mapNotNull { missing.takeIfPresent(jar = it, owner = libraryMoniker) }
  }

  /**
   * A library's files in the Bazel runfiles, jar by jar: the per-jar label, or else the jar file of the container.
   *
   * The per-jar label of a Maven library is a `copy_file` output, and the container imports the downloaded file itself.
   * A binary whose data is `dist_plugin_content` holds the container jars only.
   */
  private fun resolveLibraryRunfiles(library: BazelTargetsInfo.LibraryDescription, libraryMoniker: String): List<BazelOutputJar> {
    check(library.jarTargets.size == library.jars.size) { "The jar labels and the jar paths of $libraryMoniker differ in count" }
    return library.jarTargets.zip(library.jars) { jarTarget, jar ->
      findLibraryJarRunfile(jarTarget = jarTarget, jar = jar, findByLabel = ::findRunfileByLabel, findByKey = ::findRunfile)
        ?.let { BazelOutputJar(label = jarTarget, file = it) }
      ?: resolveRunfileJar(jarTarget)
    }
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val missing = MissingBazelOutputs(state.mode)
    val jars = collectModuleOutputRoots(module = module, forTests = forTests, missing = missing)
    missing.throwIfAny(subject = moduleMoniker(module, forTests))
    return jars
  }

  fun findPluginDistributionTargetDescription(mainModuleName: String) : BazelTargetsInfo.PluginDistributionTargetDescription? {
    return state.bazelTargetsMap.pluginDistributionTargets[mainModuleName]
  }

  /**
   * The output roots of a module, as [getModuleOutputRoots] gives them. An absent jar goes into [missing], and the caller
   * reports all absent jars of its request at once.
   */
  internal fun collectModuleOutputRoots(module: JpsModule, forTests: Boolean, missing: MissingBazelOutputs): List<Path> {
    val moduleDescription = findModuleDescription(module, forTests)
    val targets = if (forTests) moduleDescription.testTargets else moduleDescription.productionTargets
    val jars = when (state.mode) {
      BazelOutputMode.RUNFILES -> targets.map(::resolveRunfileJar)
      BazelOutputMode.OUTSIDE_BAZEL -> {
        // `productionJars` and `productionTargets` are parallel lists, and so are the test lists.
        val jarsRelative = if (forTests) moduleDescription.testJars else moduleDescription.productionJars
        jarsRelative.mapIndexed { index, jar -> BazelOutputJar(label = targets.getOrNull(index), file = state.projectHome.resolve(jar)) }
      }
    }
    val owner = moduleMoniker(module, forTests)
    return jars.mapNotNull { missing.takeIfPresent(jar = it, owner = owner) }
  }

  private fun moduleMoniker(module: JpsModule, forTests: Boolean): String = "module '${module.name}'" + if (forTests) " (tests)" else ""

  /**
   * The roots that a probe of [module] reads. Each mode gives the roots by its own rule.
   *
   * Under Bazel runfiles, [resolveProbeRoot] gives one root for each module target.
   * That root is the resources sibling jar, or the module jar when the sibling is absent.
   * A module target has no root when both files are absent.
   * Outside Bazel, the roots are the module jars in the project home.
   */
  private fun computeProbeRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val moduleDescription = findModuleDescription(module, forTests)
    val targets = if (forTests) moduleDescription.testTargets else moduleDescription.productionTargets
    return when (state.mode) {
      BazelOutputMode.RUNFILES -> targets.mapNotNull { resolveProbeRoot(moduleTarget = it, resolveFile = ::findRunfileByLabel) }
      BazelOutputMode.OUTSIDE_BAZEL -> {
        val jarsRelative = if (forTests) moduleDescription.testJars else moduleDescription.productionJars
        jarsRelative.map { state.projectHome.resolve(it) }
      }
    }
  }

  private fun findModuleDescription(module: JpsModule, forTests: Boolean): BazelTargetsInfo.TargetsFileModuleDescription {
    val moduleDescription = state.bazelTargetsMap.modules.get(module.name) ?: error("Cannot find module '${module.name}' in the project")

    if (forTests && !isTestCompilationOutputEnabled(module)) {
      error(
        "Cannot find test sources for module '${module.name}' because 'useTestSourceEnabled' is false.\n" +
        "System property '${BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY}' value: ${System.getProperty(BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY)}, " +
        "selective modules: $testCompilationOutputModules, " +
        "BazelModuleOutputProvider.useTestCompilationOutput (from BuildOptions.useTestCompilationOutput) value: $useTestCompilationOutput, " +
        "default value: ${BuildOptions.USE_TEST_COMPILATION_OUTPUT_DEFAULT_VALUE}"
      )
    }
    return moduleDescription
  }

  /**
   * Answers from [ModuleOutputEntryIndex] when [ModuleOutputEntryIndex.isIndexedName] accepts [relativePath], and scans
   * every module output otherwise.
   */
  override fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
    if (ModuleOutputEntryIndex.isIndexedName(relativePath)) {
      return outputEntryIndex.find(
        relativePath = relativePath,
        moduleNamePrefix = moduleNamePrefix,
        processedModules = processedModules,
      ) { module ->
        readFileContentFromModuleOutput(module = module, relativePath = relativePath, forTests = false)
      }
    }
    return findFileInAnyModuleOutput(
      modules = state.modules,
      relativePath = relativePath,
      provider = this,
      moduleNamePrefix = moduleNamePrefix,
      processedModules = processedModules,
    )
  }

  override fun getModuleImlFile(module: JpsModule): Path = state.getModuleImlFile(module)

  override fun toString(): String = "BazelModuleOutputProvider(projectHome=${state.projectHome}, bazelOutputRoot=${state.resolvedBazelOutputRoot ?: "<not resolved>"})"
}

/** Where a build reads the Bazel outputs from. The mode decides how a label resolves to a file, and what fixes an absent file. */
@Internal
enum class BazelOutputMode {
  /** `bazel run` or `bazel test` gives the runfiles of the target. */
  RUNFILES,

  /** No Bazel process runs the build. The files are in the Bazel output directories of the checkout. */
  OUTSIDE_BAZEL;

  companion object {
    fun current(): BazelOutputMode = when {
      BazelRunfiles.isRunningFromBazel -> RUNFILES
      else -> OUTSIDE_BAZEL
    }
  }
}

/**
 * One jar of a request.
 *
 * [label] is the Bazel target that produces the jar, or `null` when `bazel-targets.json` names none. [file] is `null`
 * when the label does not resolve to a path. [detail] tells why it does not resolve.
 */
internal class BazelOutputJar(@JvmField val label: String?, @JvmField val file: Path?, @JvmField val detail: String? = null)

/** The runfile of [label]. When the runfiles do not hold it, the jar has no file, and the reason is the detail. */
private fun resolveRunfileJar(label: String): BazelOutputJar {
  return try {
    BazelOutputJar(label = label, file = BazelRunfiles.getFileByLabel(BazelLabel.fromString(label)))
  }
  catch (e: IllegalStateException) {
    BazelOutputJar(label = label, file = null, detail = e.message)
  }
}

/**
 * The absent jars of one request, so that one error names all of them.
 *
 * A request is one module, one library or one runtime classpath. [throwIfAny] gives the fix for the [mode].
 */
internal class MissingBazelOutputs(private val mode: BazelOutputMode) {
  /**
   * By the file, else by the label. A multi-jar library can give one label to several files: the container in place of
   * an absent per-jar label.
   */
  private val jars = LinkedHashMap<String, MissingBazelOutput>()

  /** Returns the file of [jar] when it is a regular file. Otherwise records the jar and returns `null`. */
  fun takeIfPresent(jar: BazelOutputJar, owner: String): Path? {
    val file = jar.file
    if (file != null && file.isRegularFile()) {
      return file
    }
    val key = file?.toString() ?: jar.label ?: "<no label and no file> #${jars.size}"
    jars.putIfAbsent(key, MissingBazelOutput(jar = jar, owner = owner))
    return null
  }

  /**
   * Throws one [IllegalStateException] that names every absent jar, when there is one.
   *
   * [labelFileDirectory] holds the label file for a command that is too long to read. Without it, the message gives the
   * full command in one line. [starterHint] adds the IDE Starter workaround. Only a runtime classpath asks for it: the
   * test class index logs the message of each module that it skips, and that message must stay short.
   */
  fun throwIfAny(subject: String, labelFileDirectory: Path? = null, starterHint: Boolean = false) {
    if (jars.isNotEmpty()) {
      throw IllegalStateException(message(subject = subject, labelFileDirectory = labelFileDirectory, starterHint = starterHint))
    }
  }

  private fun message(subject: String, labelFileDirectory: Path?, starterHint: Boolean): String {
    val entries = jars.values.toList()
    val one = entries.size == 1
    return buildString {
      append(entries.size).append(if (one) " jar of " else " jars of ").append(subject)
      append(
        when (mode) {
          BazelOutputMode.OUTSIDE_BAZEL -> if (one) " does not exist" else " do not exist"
          BazelOutputMode.RUNFILES -> if (one) " is not in the Bazel runfiles" else " are not in the Bazel runfiles"
        }
      )
      append(":\n")
      for (entry in entries.take(MAX_LISTED_MISSING_JARS)) {
        append("  ").append(entry.jar.label ?: "<no label>").append(" (").append(entry.owner).append(')')
        entry.jar.file?.let { append(": ").append(it) }
        append('\n')
        entry.jar.detail?.lineSequence()?.filter { it.isNotBlank() }?.forEach { append("    ").append(it).append('\n') }
      }
      if (entries.size > MAX_LISTED_MISSING_JARS) {
        append("  ... and ").append(entries.size - MAX_LISTED_MISSING_JARS).append(" more\n")
      }
      when (mode) {
        BazelOutputMode.OUTSIDE_BAZEL -> {
          val labels = entries.mapNotNull { it.jar.label }.distinct()
          if (labels.isNotEmpty()) {
            append("Build ").append(if (one) "it" else "them").append(": ")
            append(buildCommand(labels = labels, labelFileDirectory = labelFileDirectory))
          }
          if (starterHint) {
            append("\nFor an IDE Starter test, you can build the distribution of the product instead, for example ")
            append("`./bazel.cmd build //build:idea_dist`. Then give the test JVM ")
            append("`-Didea.ide.config.path=<absolute path of out/bazel-bin/build/idea_dist.ide.config>`, ")
            append("and the test runs on that distribution.")
          }
        }
        BazelOutputMode.RUNFILES -> {
          append("Declare these labels in the `data` of the Bazel target that runs this build. ")
          append("`./bazel.cmd build` does not help, because a Bazel target reads only the files that it declares.")
        }
      }
    }
  }

  private fun buildCommand(labels: List<String>, labelFileDirectory: Path?): String {
    if (labels.size > MAX_LISTED_MISSING_JARS && labelFileDirectory != null) {
      writeLabelFile(labels, labelFileDirectory)?.let { return "./bazel.cmd build --target_pattern_file=$it" }
    }
    return labels.joinToString(separator = " ", prefix = "./bazel.cmd build ")
  }

  /** The file with one label per line, or `null` when it cannot be written. The message then gives the full command. */
  private fun writeLabelFile(labels: List<String>, directory: Path): Path? {
    return try {
      Files.createDirectories(directory)
      val file = Files.createTempFile(directory, "missing-bazel-outputs-", ".txt")
      Files.writeString(file, labels.joinToString(separator = "\n", postfix = "\n"))
      file
    }
    catch (_: IOException) {
      null
    }
  }
}

private class MissingBazelOutput(@JvmField val jar: BazelOutputJar, @JvmField val owner: String)

/** The message lists this many absent jars. A longer command goes into a label file when the caller gives a directory for it. */
internal const val MAX_LISTED_MISSING_JARS = 50

/** The entries of the runfiles manifest, or `null` when the runfiles have no manifest. */
private val runfilesManifestEntries: Map<String, String>? by lazy {
  BazelRunfilesManifest().takeIf { it.exists }?.entries
}

/**
 * The file of [label] in the Bazel runfiles, or `null` when the runfiles do not hold it.
 *
 * It looks up the exact manifest key, and it uses the runfiles tree when the runfiles have no manifest.
 */
private fun findRunfileByLabel(label: String): Path? {
  val bazelLabel = BazelLabel.fromString(label)
  val repoEntry = BazelRunfiles.bazelTestRepoMapping.get(bazelLabel.repo) ?: return null
  val key = buildString {
    append(repoEntry.runfilesRelativePath)
    if (bazelLabel.packageName.isNotEmpty()) {
      append('/')
      append(bazelLabel.packageName)
    }
    append('/')
    append(bazelLabel.target)
  }
  return findRunfile(key)
}

/**
 * The runfile of one library jar, or `null` when the runfiles hold neither file.
 *
 * [findByLabel] gives the file of the per-jar label [jarTarget]. [findByKey] gives the file of a runfiles key, here the
 * key of the container jar [jar].
 */
internal fun findLibraryJarRunfile(jarTarget: String, jar: String, findByLabel: (String) -> Path?, findByKey: (String) -> Path?): Path? {
  return findByLabel(jarTarget) ?: runfilesKeyOfOutputPath(jar)?.let(findByKey)
}

/**
 * The runfiles key of [outputPath], or `null` when [outputPath] is in no repository.
 *
 * [outputPath] is relative to the output base, as `LibraryDescription.jars` states it: `external/<repository>/...` or
 * `execroot/_main/...`. The key is the same path without the first element.
 */
internal fun runfilesKeyOfOutputPath(outputPath: String): String? {
  val path = Path.of(outputPath).normalize()
  if (path.nameCount < 3 || path.getName(0).toString() !in setOf("external", "execroot")) {
    return null
  }
  return path.subpath(1, path.nameCount).invariantSeparatorsPathString
}

private fun findRunfile(key: String): Path? {
  val entries = runfilesManifestEntries
  val file = if (entries == null) BazelRunfiles.bazelJavaRunfilesPath.resolve(key) else Path.of(entries.get(key) ?: return null)
  return if (Files.exists(file)) file else null
}

/**
 * The label of the resources sibling jar of [moduleTarget], or `null` when [moduleTarget] is not a jar label.
 *
 * The sibling of `<package>:<name>.jar` is `<package>:<name>_resource_jar.jar`. The repository prefix stays.
 * The `jvm_library` macro declares the `<name>_resource_jar` target for every module.
 */
internal fun resourcesSiblingLabel(moduleTarget: String): String? {
  val targetStart = moduleTarget.lastIndexOf(':') + 1
  if (targetStart == 0 || !moduleTarget.endsWith(".jar")) {
    return null
  }
  val nameStart = maxOf(targetStart, moduleTarget.lastIndexOf('/') + 1)
  val nameEnd = moduleTarget.length - ".jar".length
  if (nameStart >= nameEnd) {
    return null
  }
  return moduleTarget.substring(0, nameEnd) + "_resource_jar.jar"
}

/**
 * The probe root of [moduleTarget] under plain Bazel runfiles. [resolveFile] gives the file of a label, or `null`.
 *
 * The resources sibling jar comes first. The module jar is the root when the sibling is absent.
 * The module target has no root when both are absent.
 */
internal fun resolveProbeRoot(moduleTarget: String, resolveFile: (String) -> Path?): Path? {
  resourcesSiblingLabel(moduleTarget)?.let(resolveFile)?.let { return it }
  return resolveFile(moduleTarget)
}

/**
 * Searches for a file across module outputs.
 * If [moduleNamePrefix] is specified, only searches in modules whose name starts with the prefix.
 * If [processedModules] is specified, skips modules already in the set and adds searched modules to it.
 *
 * The answer is the first module in the order of [modules] that has the file, or the first failure before it. The
 * modules are read in chunks on several threads, because a read opens the output of a module, and a search over the
 * whole project opens thousands of them. A chunk after the first hit is skipped when the hit is known in time.
 */
internal fun findFileInAnyModuleOutput(
  modules: Iterable<JpsModule>,
  relativePath: String,
  provider: ModuleOutputProvider,
  moduleNamePrefix: String? = null,
  processedModules: MutableSet<String>? = null,
): ByteArray? {
  val candidates = ArrayList<JpsModule>()
  for (module in modules) {
    val name = module.name
    if (moduleNamePrefix != null && !name.startsWith(moduleNamePrefix)) {
      continue
    }
    if (processedModules != null && !processedModules.add(name)) {
      continue
    }
    candidates.add(module)
  }

  val chunks = candidates.chunked(ANY_MODULE_OUTPUT_SEARCH_CHUNK)
  val firstHit = AtomicInteger(Int.MAX_VALUE)
  val outcomes = chunks.withIndex().toList().mapConcurrent { (index, chunk) ->
    if (index > firstHit.get()) {
      return@mapConcurrent null
    }
    try {
      chunk.firstNotNullOfOrNull { module ->
        provider.readFileContentFromModuleOutput(module = module, relativePath = relativePath, forTests = false)
      }?.let { data ->
        firstHit.accumulateAndGet(index, ::minOf)
        Result.success(data)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: InterruptedException) {
      throw e
    }
    catch (e: Exception) {
      Result.failure(e)
    }
  }
  for (outcome in outcomes) {
    if (outcome != null) {
      return outcome.getOrThrow()
    }
  }
  return null
}

private const val ANY_MODULE_OUTPUT_SEARCH_CHUNK = 64

/**
 * Maps an entry name to the modules whose production outputs hold it.
 *
 * The index keeps only the names that [isIndexedName] accepts, because a descriptor search asks only for such names.
 * A module whose outputs give no list of names is an unlisted module. A search reads an unlisted module directly.
 */
internal class ModuleOutputEntryIndex private constructor(
  private val modules: List<JpsModule>,
  /** The positions in [modules] of the modules that hold the name, in ascending order. */
  private val positionsByName: Map<String, IntArray>,
  private val unlistedPositions: BitSet,
) {
  /**
   * Gives the same answer as the scan of [findFileInAnyModuleOutput] over [modules].
   *
   * The search adds every candidate to [processedModules] before it reads a module. It calls [read] for an unlisted
   * candidate and for a candidate that holds [relativePath], in the order of [modules]. The first result that is
   * not `null` is the answer, and the first failure of [read] stops the search.
   */
  fun find(
    relativePath: String,
    moduleNamePrefix: String?,
    processedModules: MutableSet<String>?,
    read: (JpsModule) -> ByteArray?,
  ): ByteArray? {
    val candidates = IntArrayList()
    for ((position, module) in modules.withIndex()) {
      val name = module.name
      if (moduleNamePrefix != null && !name.startsWith(moduleNamePrefix)) {
        continue
      }
      if (processedModules != null && !processedModules.add(name)) {
        continue
      }
      candidates.add(position)
    }

    val holders = positionsByName.get(relativePath)
    for (i in candidates.indices) {
      val position = candidates.getInt(i)
      if (unlistedPositions.get(position) || (holders != null && holders.binarySearch(position) >= 0)) {
        read(modules.get(position))?.let { return it }
      }
    }
    return null
  }

  companion object {
    /** Accepts a relative path that ends with `.xml` and does not start with a slash. */
    fun isIndexedName(name: String): Boolean = name.endsWith(".xml") && !name.startsWith('/')

    /**
     * Lists the names of every module in parallel. [listNames] returns `null` for an unlisted module.
     * A failure of [listNames] also makes the module unlisted, so a search reads it and gets the same failure.
     */
    fun build(modules: List<JpsModule>, listNames: (JpsModule) -> List<String>?): ModuleOutputEntryIndex {
      return buildSpan("index module outputs") { span ->
        val namesByModule = modules.mapConcurrent { module ->
          try {
            listNames(module)
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: InterruptedException) {
            throw e
          }
          catch (_: Exception) {
            null
          }
        }

        val positionLists = HashMap<String, IntArrayList>()
        val unlistedPositions = BitSet()
        var entryCount = 0L
        for ((position, names) in namesByModule.withIndex()) {
          if (names == null) {
            unlistedPositions.set(position)
            continue
          }
          for (name in names) {
            val positions = positionLists.computeIfAbsent(name) { IntArrayList(1) }
            // two output roots of one module can hold the same name
            if (positions.isEmpty || positions.getInt(positions.size - 1) != position) {
              positions.add(position)
              entryCount++
            }
          }
        }

        val positionsByName = HashMap<String, IntArray>(positionLists.size)
        for ((name, positions) in positionLists) {
          positionsByName.put(name, positions.toIntArray())
        }
        span.setAttribute("moduleCount", modules.size.toLong())
        span.setAttribute("entryCount", entryCount)
        span.setAttribute("nameCount", positionsByName.size.toLong())
        span.setAttribute("unlistedModuleCount", unlistedPositions.cardinality().toLong())
        ModuleOutputEntryIndex(modules = modules, positionsByName = positionsByName, unlistedPositions = unlistedPositions)
      }
    }
  }
}
