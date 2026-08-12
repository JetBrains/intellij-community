// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")
@file:JvmName("DevDistMain")

package org.jetbrains.intellij.build.devServer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.system.exitProcess

/**
 * Assembles a dev distribution into a caller-specified output directory and writes the config file that
 * `PreBuiltDevMain` consumes (`home.path` / `main.class.name`).
 *
 * Unlike `DevMainImpl`, which builds and launches in the same process from an IDE run configuration, this entry point only builds:
 * the run directory is given from the outside ([BuildRequest.runDirOverride]), the build scratch is kept out of it
 * ([BuildRequest.scratchDir]), and everything else is passed explicitly instead of being read from system properties,
 * so that a caller (a Bazel action, a CI step) fully controls the result.
 *
 * This entry point is product-agnostic: the product is selected by `--platform-prefix`.
 */
fun main(args: Array<String>) {
  val options = parseArgs(args)

  val projectDir = options.requiredPath("--project-dir") { System.getenv("BUILD_WORKSPACE_DIRECTORY") }
  // `BuildPaths.COMMUNITY_ROOT` and `ULTIMATE_HOME` are lazily initialized singletons that guess the repository root by walking up from
  // a set of candidate locations (see `IdeaProjectLoaderUtil.collectHomeSources`). Inside a Bazel action none of those candidates work:
  // there is no `BUILD_WORKSPACE_DIRECTORY`, the working directory is an execroot, and the jar location is in the output base -
  // no repository marker file is reachable from any of them. This property is the highest-priority source in that list,
  // so it must be set before any code touches those singletons.
  System.setProperty("intellij.build.ultimate.home.path", projectDir.invariantSeparatorsPathString)

  val outputDir = options.requiredPath("--output-dir")
  val scratchDir = options.optionalPath("--scratch-dir") ?: Path.of("${outputDir.invariantSeparatorsPathString}.scratch")
  val ideConfigFile = options.requiredPath("--ide-config")
  val platformPrefix = options.optional("--platform-prefix") ?: "idea"
  val additionalModules = options.list("--additional-module")
  val os = options.optional("--os")?.let(::parseOs) ?: OsFamily.currentOs
  val buildDateInSeconds = options.optional("--build-date-seconds")?.let {
    it.toLongOrNull() ?: error("--build-date-seconds must be an integer number of seconds since the epoch, but got '$it'")
  }
  // a dev run directory is disposable and may share bytes with the jar cache, but a Bazel output must own its bytes
  val linkCacheEntries = options.optionalBoolean("--link-cache-entries") ?: false
  val generateRuntimeModuleRepository = options.optionalBoolean("--generate-runtime-module-repository") ?: false
  options.checkNoUnknownOptions()

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
      )
    )

    withContext(Dispatchers.IO) {
      ideConfigFile.parent?.createDirectories()
      // read back by `PreBuiltDevMain.readIdeConfig` as a `java.util.Properties` file -
      // invariant separators keep Windows paths free of `Properties` backslash escapes
      Files.writeString(ideConfigFile, "home.path=${runDir.invariantSeparatorsPathString}\nmain.class.name=$mainClassName\n")
    }
    runDir
  }

  println("Dev distribution assembled into $runDir (main class: $mainClassName, config: $ideConfigFile)")
  // the build uses thread pools and Netty/Ktor selectors that may outlive the last coroutine
  exitProcess(0)
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
