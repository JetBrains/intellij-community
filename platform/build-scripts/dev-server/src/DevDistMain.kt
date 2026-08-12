// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")
@file:JvmName("DevDistMain")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.devIdeConfig.DevIdeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.materializeProjectModelTree
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
 * `--jar-cache-dir` names one. That is what an `intellij_dev_dist` Bazel action passes.
 *
 * A distribution meant for a test lane also passes `--pack-test-sources`, which is what lets `--additional-module` name
 * a plugin whose content comes from test compilation output.
 */
@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
  val options = parseArgs(args)

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
    materializeProjectModelTree(manifest = projectManifest, target = scratchDir.resolve("project"))
  }
  // `BuildPaths.COMMUNITY_ROOT` and `ULTIMATE_HOME` are lazily initialized singletons that guess the repository root by walking up from
  // a set of candidate locations (see `IdeaProjectLoaderUtil.collectHomeSources`). Inside a Bazel action none of those candidates work:
  // there is no `BUILD_WORKSPACE_DIRECTORY`, the working directory is an execroot, and the jar location is in the output base -
  // no repository marker file is reachable from any of them. This property is the highest-priority source in that list,
  // so it must be set before any code touches those singletons.
  System.setProperty("intellij.build.ultimate.home.path", projectDir.invariantSeparatorsPathString)

  val ideConfigFile = options.requiredPath("--ide-config")
  val platformPrefix = options.optional("--platform-prefix") ?: "idea"
  val additionalModules = options.list("--additional-module")
  val os = options.optional("--os")?.let(::parseOs) ?: OsFamily.currentOs
  val buildDateInSeconds = options.optional("--build-date-seconds")?.let {
    it.toLongOrNull() ?: error("--build-date-seconds must be an integer number of seconds since the epoch, but got '$it'")
  }
  // a dev run directory is disposable and may share bytes with the jar cache, but a Bazel output must own its bytes
  val linkCacheEntries = options.optionalBoolean("--link-cache-entries") ?: false
  // Unlike a dev run directory, which is rebuilt in place over and over and reuses a jar cache shared with every other
  // product, an assembly here is produced once per change by a caller that caches the whole result. A local disk cache would
  // only add a second copy of every jar, and a directory that concurrent assemblies mutate while its cleanup prunes it.
  val jarCacheDir = options.optionalPath("--jar-cache-dir")
  val generateRuntimeModuleRepository = options.optionalBoolean("--generate-runtime-module-repository") ?: false
  // A test-only plugin - a lambda test plugin, a fixture plugin - is packed from test compilation output, which
  // `BazelModuleOutputProvider` serves only when this is on. It is a property rather than a `BuildRequest` field because
  // `BuildOptions` already owns the knob and reads it when it is constructed, inside the build.
  if (options.optionalBoolean("--pack-test-sources") == true) {
    System.setProperty(BuildOptions.USE_TEST_COMPILATION_OUTPUT_PROPERTY, "true")
  }
  // the output directory must be empty (see `BuildRequest.runDirOverride`). A Bazel action always gets an empty declared
  // directory, so this is for a standalone caller re-running the assembler into a path it already used.
  val cleanOutput = options.optionalBoolean("--clean-output") ?: false
  configurePreloadedDownloads(options)
  options.checkNoUnknownOptions()

  if (cleanOutput && Files.exists(outputDir)) {
    outputDir.deleteRecursively()
  }

  lateinit var mainClassName: String

  val runDir = runBlocking(Dispatchers.Default) {
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
        // the IDE is started by `PreBuiltDevMain`, which resets the classloader itself, so the boot classpath is not the final one
        isBootClassPathCorrect = false,
        generateRuntimeModuleRepository = generateRuntimeModuleRepository,
        runDirOverride = outputDir,
        scratchDir = scratchDir,
        buildDateInSeconds = buildDateInSeconds,
        linkImmutableCacheEntries = linkCacheEntries,
        jarCacheDir = jarCacheDir,
      )
    )

    withContext(Dispatchers.IO) {
      dropEmptyTempDir(runDir)
      // What the distribution is, not just where it is: a consumer that needs a different product or a plugin module
      // this assembly did not build in is looking at the wrong distribution, and `DevIdeConfig` is where it can find
      // that out. The relative-home rule and the file format live there too, with the readers.
      DevIdeConfig.write(ideConfigFile, runDir, mainClassName, platformPrefix, additionalModules)
    }
    runDir
  }

  println("Dev distribution assembled into $runDir (main class: $mainClassName, config: $ideConfigFile)")
  // the build uses thread pools and Netty/Ktor selectors that may outlive the last coroutine
  exitProcess(0)
}

/**
 * Points the downloader at archives the caller has already fetched, instead of letting it reach the network.
 *
 * The manifests are named as absolute paths, which [org.jetbrains.intellij.build.dependencies.PreloadedDownloads] takes
 * verbatim; only a relative name is resolved against the runfiles tree. That is what lets the archives be plain inputs of
 * a Bazel action rather than runfiles of the assembler binary.
 */
private fun configurePreloadedDownloads(options: Options) {
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

private fun parseOs(value: String): OsFamily {
  return OsFamily.entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.osId.equals(value, ignoreCase = true) || it.dirName.equals(value, ignoreCase = true) }
         ?: error("Unknown --os value '$value', expected one of ${OsFamily.entries.joinToString { it.osId }}")
}

private class Options(private val values: Map<String, List<String>>) {
  private val used = HashSet<String>()

  fun optional(name: String): String? {
    used.add(name)
    val value = values.get(name) ?: return null
    require(value.size == 1) { "$name must be specified at most once, but got ${value.size} values: $value" }
    return value.single().takeIf { it.isNotEmpty() }
  }

  fun list(name: String): List<String> {
    used.add(name)
    return values.get(name)?.filter { it.isNotEmpty() } ?: emptyList()
  }

  fun optionalBoolean(name: String): Boolean? {
    val value = optional(name) ?: return null
    return when (value.lowercase()) {
      "true" -> true
      "false" -> false
      else -> error("$name must be 'true' or 'false', but got '$value'")
    }
  }

  fun optionalPath(name: String): Path? = optional(name)?.let { toAbsolutePath(it) }

  fun pathList(name: String): List<Path> = list(name).map { toAbsolutePath(it) }

  fun requiredPath(name: String, fallback: () -> String? = { null }): Path {
    val value = optional(name) ?: fallback() ?: error("$name is required (no value and no fallback available)")
    return toAbsolutePath(value)
  }

  fun checkNoUnknownOptions() {
    val unknown = values.keys - used
    check(unknown.isEmpty()) { "Unknown options: ${unknown.sorted().joinToString()}" }
  }

  // all paths are made absolute right away: the build changes neither the working directory nor its own view of it,
  // but the resulting paths are written into files and compared to each other, so they must not depend on it
  private fun toAbsolutePath(value: String): Path = Path.of(value).toAbsolutePath().normalize()
}

private fun parseArgs(args: Array<String>): Options {
  val values = LinkedHashMap<String, MutableList<String>>()
  for (arg in args) {
    require(arg.startsWith("--")) { "Expected an option in the '--key=value' form, but got '$arg'" }
    val separatorIndex = arg.indexOf('=')
    // a flag without a value is a `true` flag
    val name = if (separatorIndex == -1) arg else arg.substring(0, separatorIndex)
    val value = if (separatorIndex == -1) "true" else arg.substring(separatorIndex + 1)
    values.computeIfAbsent(name) { ArrayList() }.add(value)
  }
  return Options(values)
}
