// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistMain")

package org.jetbrains.intellij.build.devServer

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.DevBuildFragment
import org.jetbrains.intellij.build.dev.DevBuildOutput
import org.jetbrains.intellij.build.dev.DevDistRecipe
import org.jetbrains.intellij.build.dev.PlatformJarSelector
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.impl.BazelBuildInputs
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.system.exitProcess

/**
 * Assembles a reference fragment of a dev distribution into a caller-specified output directory. A reference is the
 * second producer of the `jars`, `replay` and `runtime-repo` gates, and no distribution composes it.
 *
 * Unlike `DevMainImpl`, which builds and launches in the same process from an IDE run configuration, this entry point only
 * builds. The Bazel action gives the run directory ([BuildRequest.runDirOverride]) and the build scratch
 * ([BuildRequest.scratchDir]), and it passes everything else explicitly instead of through system properties.
 *
 * `--platform-prefix` selects the product. `--project-dir` names the project model tree the action declares, and
 * `--preloaded-manifest` supplies the archives a build would otherwise download. `--os` and `--arch` select the
 * complete target platform.
 *
 * [TRACE_FILE_OPTION] writes this process's spans out as a side output; without it nothing is written.
 * `--plan` does the same for the packaging recipe this assembly executed. See [DevDistRecipe].
 */
fun main(args: Array<String>) {
  val options = parseCommandLineOptions(args)
  runDevDistJob(traceFile = options.optionalPath(TRACE_FILE_OPTION), jobName = "assemble dev distribution") {
    assembleDevDistribution(options)
  }
  // the build uses thread pools and Netty/Ktor selectors that may outlive the last coroutine
  exitProcess(0)
}

@OptIn(ExperimentalPathApi::class)
private fun assembleDevDistribution(options: CommandLineOptions) {
  val outputDir = options.requiredPath("--output-dir")
  val scratchDir = options.requiredPath("--scratch-dir")
  // A Bazel action names the shared project model tree it declares. It cannot read the checkout, because the checkout is
  // not an input of anything.
  val projectDir = options.requiredPath("--project-dir")
  // `BuildPaths.COMMUNITY_ROOT` and `ULTIMATE_HOME` are lazily initialized singletons that guess the repository root by walking up from
  // a set of candidate locations (see `IdeaProjectLoaderUtil.collectHomeSources`). Inside a Bazel action none of those candidates work:
  // there is no `BUILD_WORKSPACE_DIRECTORY`, the working directory is an execroot, and the jar location is in the output base -
  // no repository marker file is reachable from any of them. This property is the highest-priority source in that list,
  // so it must be set before any code touches those singletons.
  System.setProperty("intellij.build.ultimate.home.path", projectDir.invariantSeparatorsPathString)

  val platformPrefix = options.optional("--platform-prefix") ?: error("--platform-prefix is required")
  val os = options.optional("--os")?.let(::parseOs) ?: OsFamily.currentOs
  val arch = options.optional("--arch")?.let(::parseArch) ?: JvmArchitecture.currentJvmArch
  val buildDateInSeconds = options.optional("--build-date-seconds")?.let {
    it.toLongOrNull() ?: error("--build-date-seconds must be an integer number of seconds since the epoch, but got '$it'")
  }
  val cleanScratchOnSuccess = options.optionalBoolean("--clean-scratch-on-success") ?: false
  val fragment = parseFragment(options)
  // the root span is what a merged timeline groups an action's spans under, and every fragment action opens the same
  // one, so it has to say which fragment it was
  Span.current().setAttribute("fragment", fragment.name)
  val output = DevBuildOutput.Component(fragment = fragment, manifestFile = options.requiredPath("--component-manifest"))
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
  val unusedInputs = options.optionalPath("--unused-inputs")
  // The packaging recipe this assembly is about to execute, written after it has executed it. A pure side output: with
  // the option absent nothing is recorded and nothing is written, so an assembly that is not asked for its recipe is
  // byte-for-byte the assembly it was.
  val planFile = options.optionalPath("--plan")
  // The layout the runtime module repository fragment generates the repository from, a side output like the recipe.
  val runtimeModuleRepositoryLayoutFile = options.optionalPath("--runtime-module-repository-layout")
  configurePreloadedDownloads(options)
  options.checkNoUnknownOptions()

  if (planFile != null) {
    DevDistRecipe.start(distRoot = outputDir, projectHome = projectDir, scratchDir = scratchDir)
  }

  val build = buildProductInProcess(
    BuildRequest(
      platformPrefix = platformPrefix,
      additionalModules = emptyList(),
      projectDir = projectDir,
      os = os,
      arch = arch,
      runtimeModuleRepositoryLayoutFile = runtimeModuleRepositoryLayoutFile,
      runDirOverride = outputDir,
      scratchDir = scratchDir,
      buildDateInSeconds = buildDateInSeconds,
      // A caller that caches the whole result produces a reference once per change. A local jar cache would only add a
      // second copy of every jar, in a directory that concurrent assemblies change while its cleanup prunes it.
      jarCacheDir = null,
      output = output,
    )
  )
  val runDir = build.runDir
  val mainClassName = build.mainClass

  dropEmptyTempDir(runDir)

  planFile?.let {
    DevDistRecipe.write(file = it, fragment = fragment.name)
  }

  println("Dev distribution fragment '$fragment' assembled into $runDir (main class: $mainClassName)")
  if (cleanScratchOnSuccess) {
    scratchDir.deleteRecursively()
    Files.createDirectories(scratchDir)
  }
  unusedInputs?.let(BazelBuildInputs::writeUnusedInputs)
}

/** Reads which slice of a distribution to assemble. Nothing is inferred: a fragment names itself and its selectors. */
private fun parseFragment(options: CommandLineOptions): DevBuildFragment {
  val name = options.optional("--fragment") ?: error("--fragment is required")
  val platform = options.optional("--platform")?.let { value ->
    // The jars are named rather than derived: the reference of the `jars` gate packs exactly the jars of the packed-jars
    // component, and both sides read one generated list.
    val jars = options.list("--platform-jar").toSet()
    when (value) {
      "only" -> PlatformJarSelector(jars = jars, mode = PlatformJarSelector.Mode.ONLY)
      else -> error("Unknown --platform value '$value', expected only")
    }
  }
  val runtimeModuleRepository = options.optionalBoolean("--runtime-module-repository") ?: false
  require(platform != null || runtimeModuleRepository) {
    "The '$name' fragment selects nothing: pass at least one of --platform, --runtime-module-repository"
  }
  return DevBuildFragment(name = name, platform = platform, runtimeModuleRepository = runtimeModuleRepository)
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
