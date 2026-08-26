// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistMain")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.devIdeConfig.DevIdeConfig
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.DevBuildFragment
import org.jetbrains.intellij.build.dev.DevBuildOutput
import org.jetbrains.intellij.build.dev.DevDistPatchedDescriptors
import org.jetbrains.intellij.build.dev.DevDistRecipe
import org.jetbrains.intellij.build.dev.PlatformJarSelector
import org.jetbrains.intellij.build.dev.PluginFragmentSelector
import org.jetbrains.intellij.build.dev.PrepackedPluginContentJar
import org.jetbrains.intellij.build.dev.PrepackedPluginContentKey
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.materializeProjectModelTree
import org.jetbrains.intellij.build.impl.BazelBuildInputs
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.system.exitProcess

/**
 * Assembles a dev distribution into a caller-specified output directory and writes the [DevIdeConfig] file that a
 * launcher (`PreBuiltDevMain`) or a test harness consumes.
 *
 * Unlike `DevMainImpl`, which builds and launches in the same process from an IDE run configuration, this entry point only builds:
 * the run directory is given from the outside ([BuildRequest.runDirOverride]), the build scratch is kept out of it
 * ([BuildRequest.scratchDir]), and everything else is passed explicitly instead of being read from system properties,
 * so that a caller (a Bazel action, a CI step) fully controls the result.
 *
 * This entry point is product-agnostic: the product is selected by `--platform-prefix`.
 *
 * It also runs where there is no checkout to read: `--project-manifest` builds the project model tree out of declared
 * files, `--preloaded-manifest` supplies the archives a build would otherwise download, and the jar cache is off unless
 * `--jar-cache-dir` names one. `--os` and `--arch` select the complete target platform. That is what an
 * dev-distribution fragment Bazel action passes.
 *
 * [TRACE_FILE_OPTION] writes this process's spans out as a side output; without it nothing is written.
 * `--plan` does the same for the packaging recipe this assembly executed. See [DevDistRecipe].
 * `--patched-descriptors` does it for the plugin descriptors this assembly patched. See [DevDistPatchedDescriptors].
 */
fun main(args: Array<String>) {
  val options = parseCommandLineOptions(args)
  runDevDistJob(
    traceFile = options.optionalPath(TRACE_FILE_OPTION),
    jobName = "assemble dev distribution",
    // this entry point printed a console span dump before it could write a trace file; not measuring must leave that as it was
    consoleSpansWhenNotMeasuring = true,
  ) {
    assembleDevDistribution(options)
  }
  // the build uses thread pools and Netty/Ktor selectors that may outlive the last coroutine
  exitProcess(0)
}

@OptIn(ExperimentalPathApi::class)
private suspend fun assembleDevDistribution(options: CommandLineOptions) {
  val outputDir = options.requiredPath("--output-dir")
  val scratchDir = options.optionalPath("--scratch-dir") ?: Path.of("${outputDir.invariantSeparatorsPathString}.scratch")
  // Either the caller points at a checkout, or it hands over a manifest of the project-model files it declares and gets a
  // checkout-shaped tree built out of them. A Bazel action cannot do the former: the checkout is not an input of anything,
  // so reading it would make the action's result depend on files Bazel does not track.
  val projectManifest = options.optionalPath("--project-manifest")
  val projectDir = if (projectManifest == null) {
    options.requiredPath("--project-dir") { System.getenv("BUILD_WORKSPACE_DIRECTORY") }
  }
  else {
    require(options.optional("--project-dir") == null) { "--project-dir and --project-manifest are mutually exclusive" }
    spanBuilder("materialize project model tree (inline)").blockingUse {
      materializeProjectModelTree(manifest = projectManifest, target = scratchDir.resolve("project"))
    }
  }
  // `BuildPaths.COMMUNITY_ROOT` and `ULTIMATE_HOME` are lazily initialized singletons that guess the repository root by walking up from
  // a set of candidate locations (see `IdeaProjectLoaderUtil.collectHomeSources`). Inside a Bazel action none of those candidates work:
  // there is no `BUILD_WORKSPACE_DIRECTORY`, the working directory is an execroot, and the jar location is in the output base -
  // no repository marker file is reachable from any of them. This property is the highest-priority source in that list,
  // so it must be set before any code touches those singletons.
  System.setProperty("intellij.build.ultimate.home.path", projectDir.invariantSeparatorsPathString)

  val ideConfigFile = options.optionalPath("--ide-config")
  val platformPrefix = options.optional("--platform-prefix") ?: "idea"
  val additionalModules = options.list("--additional-module")
  val testOutputModules = options.list("--test-output-module")
  if (testOutputModules.isNotEmpty()) {
    System.setProperty("idea.build.pack.test.source.modules", testOutputModules.joinToString(","))
  }
  val os = options.optional("--os")?.let(::parseOs) ?: OsFamily.currentOs
  val arch = options.optional("--arch")?.let(::parseArch) ?: JvmArchitecture.currentJvmArch
  val buildDateInSeconds = options.optional("--build-date-seconds")?.let {
    it.toLongOrNull() ?: error("--build-date-seconds must be an integer number of seconds since the epoch, but got '$it'")
  }
  // Unlike a dev run directory, which is rebuilt in place over and over and reuses a jar cache shared with every other
  // product, an assembly here is produced once per change by a caller that caches the whole result. A local disk cache would
  // only add a second copy of every jar, and a directory that concurrent assemblies mutate while its cleanup prunes it.
  val jarCacheDir = options.optionalPath("--jar-cache-dir")
  val generateRuntimeModuleRepository = options.optionalBoolean("--generate-runtime-module-repository") ?: false
  // the output directory must be empty (see `BuildRequest.runDirOverride`). A Bazel action always gets an empty declared
  // directory, so this is for a standalone caller re-running the assembler into a path it already used.
  val cleanOutput = options.optionalBoolean("--clean-output") ?: false
  val cleanScratchOnSuccess = options.optionalBoolean("--clean-scratch-on-success") ?: false
  val fragment = parseFragment(options)
  // the root span is what a merged timeline groups an action's spans under, and every fragment action opens the same
  // one, so it has to say which fragment it was
  Span.current().setAttribute("fragment", fragment.name)
  val componentManifest = options.optionalPath("--component-manifest")
  val pluginClasspathPart = options.optionalPath("--plugin-classpath-part")
  val pluginClasspathPrefix = options.optionalPath("--plugin-classpath-prefix")
  val prepackedPluginContent = options.optionalPath("--prepacked-plugin-jars")?.let(::readPrepackedPluginContentPlan).orEmpty()
  val prepackedPluginContentPlacement = options.optionalPath("--prepacked-plugin-jars-placement")
  val output = if (fragment.isComplete) {
    require(componentManifest == null && pluginClasspathPart == null && pluginClasspathPrefix == null && prepackedPluginContentPlacement == null) {
      "Component output options require --fragment"
    }
    DevBuildOutput.Complete
  }
  else {
    DevBuildOutput.Component(
      fragment = fragment,
      manifestFile = checkNotNull(componentManifest) { "--component-manifest is required for fragment '$fragment'" },
      pluginClasspathPartFile = pluginClasspathPart,
      pluginClasspathPrefixFile = pluginClasspathPrefix,
      prepackedPluginContentPlacementFile = prepackedPluginContentPlacement,
    )
  }
  options.optionalPath("--bazel-targets-json")?.let { path ->
    System.setProperty("intellij.build.bazel.targets.json.file", path.invariantSeparatorsPathString)
  }
  options.optionalPath("--bazel-inputs-manifest")?.let { path ->
    System.setProperty("intellij.build.bazel.inputs.manifest", path.invariantSeparatorsPathString)
  }
  // A build downloads and extracts into the checkout it is reading, and a project tree shared by several assemblies is
  // read-only. This is the property the platform already has for that case; the caller points it at writable scratch.
  options.optionalPath("--download-cache-dir")?.let { path ->
    System.setProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY, path.invariantSeparatorsPathString)
  }
  // The IJent binaries the distribution bundles, already unpacked by the caller. Without this the build extracts the
  // archive into the cache above just to read four files out of it, on every assembly of every fragment.
  options.optionalPath("--ijent-binaries-dir")?.let { path ->
    System.setProperty("ijent.provided.at", path.invariantSeparatorsPathString)
  }
  val unusedInputs = options.optionalPath("--unused-inputs")
  // The packaging recipe this assembly is about to execute, written after it has executed it. A pure side output: with
  // the option absent nothing is recorded and nothing is written, so an assembly that is not asked for its recipe is
  // byte-for-byte the assembly it was.
  val planFile = options.optionalPath("--plan")
  // The plugin descriptors this assembly patched, on the same terms as `--plan`. Nothing is recorded and nothing is
  // written when the option is absent. Separate from `--plan` because the two are wanted at different times. See the
  // `dev_dist_patched_descriptors` flag.
  val descriptorFile = options.optionalPath("--patched-descriptors")
  configurePreloadedDownloads(options)
  options.checkNoUnknownOptions()

  if (cleanOutput && Files.exists(outputDir)) {
    outputDir.deleteRecursively()
  }

  lateinit var mainClassName: String

  if (planFile != null) {
    DevDistRecipe.start(distRoot = outputDir, projectHome = projectDir, scratchDir = scratchDir)
  }
  if (descriptorFile != null) {
    DevDistPatchedDescriptors.start()
  }

  val runDir = buildProductInProcess(
    BuildRequest(
      platformPrefix = platformPrefix,
      additionalModules = additionalModules,
      projectDir = projectDir,
      keepHttpClient = false,
      platformClassPathConsumer = { actualMainClassName, _, _ ->
        mainClassName = actualMainClassName
      },
      os = os,
      arch = arch,
      // the IDE is started by `PreBuiltDevMain`, which resets the classloader itself, so the boot classpath is not the final one
      isBootClassPathCorrect = false,
      generateRuntimeModuleRepository = generateRuntimeModuleRepository,
      runDirOverride = outputDir,
      scratchDir = scratchDir,
      buildDateInSeconds = buildDateInSeconds,
      jarCacheDir = jarCacheDir,
      output = output,
      prepackedPluginContent = prepackedPluginContent,
    )
  )

  withContext(Dispatchers.IO) {
    dropEmptyTempDir(runDir)
    // What the distribution is, not just where it is: a consumer that needs a different product or a plugin module
    // this assembly did not build in is looking at the wrong distribution, and `DevIdeConfig` is where it can find
    // that out. The relative-home rule and the file format live there too, with the readers.
    if (fragment.isComplete) {
      DevIdeConfig.write(checkNotNull(ideConfigFile) { "--ide-config is required for a complete distribution" }, runDir, mainClassName, platformPrefix, additionalModules)
    }
  }

  planFile?.let {
    DevDistRecipe.write(file = it, fragment = fragment.name)
  }
  descriptorFile?.let {
    DevDistPatchedDescriptors.write(file = it, fragment = fragment.name)
  }

  println("Dev distribution fragment '$fragment' assembled into $runDir (main class: $mainClassName${ideConfigFile?.let { ", config: $it" }.orEmpty()})")
  if (cleanScratchOnSuccess) {
    scratchDir.deleteRecursively()
    Files.createDirectories(scratchDir)
  }
  unusedInputs?.let(BazelBuildInputs::writeUnusedInputs)
}

private fun readPrepackedPluginContentPlan(file: Path): Map<PrepackedPluginContentKey, PrepackedPluginContentJar> {
  val result = LinkedHashMap<PrepackedPluginContentKey, PrepackedPluginContentJar>()
  for ((index, line) in Files.readAllLines(file).withIndex()) {
    if (line.isBlank()) {
      continue
    }
    val fields = line.split('\t')
    require(fields.size == 3) { "$file:${index + 1}: expected plugin, content module and relative output path" }
    val jar = PrepackedPluginContentJar(
      pluginMainModule = fields[0],
      contentModule = fields[1],
      relativeOutputFile = fields[2],
    )
    val previous = result.put(jar.key, jar)
    require(previous == null) { "$file:${index + 1}: duplicate prepacked plugin relation ${jar.key}" }
  }
  return result
}

/**
 * Reads which slice of a distribution to assemble.
 *
 * Nothing is inferred: a fragment names itself and its selectors, and passing none of them means the complete
 * distribution.
 */
private fun parseFragment(options: CommandLineOptions): DevBuildFragment {
  val name = options.optional("--fragment")
  val platform = options.optional("--platform")?.let { value ->
    // The jars are named rather than derived: a fragment must own exactly the complement of what the distribution
    // composes in as the packed-jars component, and both sides read one generated list.
    val jars = options.list("--platform-jar").toSet()
    when (value) {
      "except" -> PlatformJarSelector(jars = jars, mode = PlatformJarSelector.Mode.EXCLUDE)
      "only" -> PlatformJarSelector(jars = jars, mode = PlatformJarSelector.Mode.ONLY)
      else -> error("Unknown --platform value '$value', expected except or only")
    }
  }
  val platformResources = options.optionalBoolean("--platform-resources") ?: false
  val plugins = options.optional("--plugins")?.let { value ->
    when (value) {
      "named" -> PluginFragmentSelector.Named(options.list("--plugin").toSet())
      "remaining" -> PluginFragmentSelector.Remaining(options.list("--claimed-plugin").toSet())
      else -> error("Unknown --plugins value '$value', expected named or remaining")
    }
  }

  if (name == null) {
    require(platform == null && !platformResources && plugins == null) {
      "--fragment is required to select a part of a distribution; without it the whole distribution is assembled"
    }
    return DevBuildFragment.COMPLETE
  }

  require(platform != null || platformResources || plugins != null) {
    "The '$name' fragment selects nothing: pass at least one of --platform, --platform-resources, --plugins"
  }
  return DevBuildFragment(name = name, platform = platform, platformResources = platformResources, plugins = plugins)
}

/**
 * Points the downloader at archives the caller has already fetched, instead of letting it reach the network.
 *
 * The manifests are named as absolute paths, which [org.jetbrains.intellij.build.dependencies.PreloadedDownloads] takes
 * verbatim; only a relative name is resolved against the runfiles tree. That is what lets the archives be plain inputs of
 * a Bazel action rather than runfiles of the assembler binary.
 */
private fun configurePreloadedDownloads(options: CommandLineOptions) {
  val manifests = options.pathList("--preloaded-manifest")
  if (manifests.isNotEmpty()) {
    System.setProperty(
      BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY,
      manifests.joinToString(separator = ",") { it.invariantSeparatorsPathString },
    )
  }
  if (options.optionalBoolean("--preloaded-only") == true) {
    require(manifests.isNotEmpty()) { "--preloaded-only forbids downloading, but no --preloaded-manifest declares anything" }
    System.setProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_ONLY_PROPERTY, "true")
  }
}

/**
 * Removes the empty `temp` directory the build leaves in its output directory even though the scratch is rooted elsewhere.
 * It is a stray write into what a caller declared as its distribution; a non-empty one is left alone, as that would be a
 * real finding rather than a leftover.
 */
private fun dropEmptyTempDir(runDir: Path) {
  try {
    Files.deleteIfExists(runDir.resolve("temp"))
  }
  catch (_: DirectoryNotEmptyException) {
  }
}

internal fun parseOs(value: String): OsFamily {
  return OsFamily.entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.osId.equals(value, ignoreCase = true) || it.dirName.equals(value, ignoreCase = true) }
         ?: error("Unknown --os value '$value', expected one of ${OsFamily.entries.joinToString { it.osId }}")
}

internal fun parseArch(value: String): JvmArchitecture {
  return JvmArchitecture.entries.firstOrNull {
    it.name.equals(value, ignoreCase = true) || it.archName.equals(value, ignoreCase = true) ||
    it.dirName.equals(value, ignoreCase = true) || it.marketplaceName.equals(value, ignoreCase = true)
  } ?: error("Unknown --arch value '$value', expected one of ${JvmArchitecture.entries.joinToString { it.name }}")
}
