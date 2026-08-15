// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistComposeMain")
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.devIdeConfig.DevIdeConfig
import org.jetbrains.intellij.build.dev.DevBuildComponent
import org.jetbrains.intellij.build.dev.composeDevBuildComponents
import org.jetbrains.intellij.build.dev.readDevBuildComponentManifest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
  val options = parseComposeArgs(args)
  val componentDirs = options.pathList("--component-dir")
  val componentManifestFiles = options.pathList("--component-manifest")
  require(componentDirs.isNotEmpty()) { "At least one --component-dir is required" }
  require(componentDirs.size == componentManifestFiles.size) {
    "--component-dir and --component-manifest must have the same number of values"
  }
  // One per component, positionally, and empty where a component built no plugin: a component's records cannot be
  // matched to it by name, and mismatched lists would silently attach one component's plugins to another.
  val pluginClasspathParts = options.sparsePathList("--plugin-classpath-part")
  require(pluginClasspathParts.isEmpty() || pluginClasspathParts.size == componentDirs.size) {
    "--plugin-classpath-part must be given once per --component-dir, or not at all"
  }
  val outputDir = options.requiredPath("--output-dir")
  val ideConfig = options.requiredPath("--ide-config")
  val fingerprintFile = options.requiredPath("--fingerprint")
  val pluginClasspathPrefix = options.optionalPath("--plugin-classpath-prefix")
  val expectedFragments = options.stringList("--expect-fragment")
  options.checkNoUnknownOptions()

  val components = componentDirs.mapIndexed { index, componentDir ->
    DevBuildComponent(
      root = componentDir,
      manifest = readDevBuildComponentManifest(componentManifestFiles.get(index)),
      pluginClasspathPart = pluginClasspathParts.getOrNull(index),
    )
  }

  if (Files.exists(outputDir)) outputDir.deleteRecursively()
  val result = composeDevBuildComponents(
    components = components,
    target = outputDir,
    pluginClasspathPrefix = pluginClasspathPrefix,
    expectedFragments = expectedFragments,
  )
  Files.writeString(outputDir.resolve("core-classpath.txt"), result.coreClassPath.joinToString(separator = "\n"))
  Files.writeString(outputDir.resolve("fingerprint.txt"), result.fingerprint)
  Files.writeString(fingerprintFile, result.fingerprint)
  DevIdeConfig.write(
    ideConfig,
    outputDir,
    result.mainClass,
    result.platformPrefix,
    result.additionalModules,
  )
}

private class ComposeOptions(private val values: Map<String, List<String>>) {
  private val used = HashSet<String>()

  fun requiredPath(name: String): Path {
    val paths = pathList(name)
    require(paths.size == 1) { "$name must be specified exactly once, but got ${paths.size} values" }
    return paths.single()
  }

  fun optionalPath(name: String): Path? {
    val paths = pathList(name)
    require(paths.size <= 1) { "$name must be specified at most once, but got ${paths.size} values" }
    return paths.singleOrNull()
  }

  fun pathList(name: String): List<Path> {
    return sparsePathList(name).map { checkNotNull(it) { "$name must not be empty" } }
  }

  /**
   * The paths given for [name], where an empty value is a hole rather than a path.
   *
   * That is how a caller fills a positional list in which some positions have nothing, without shifting the rest.
   */
  fun sparsePathList(name: String): List<Path?> {
    used.add(name)
    val raw = values.get(name) ?: return emptyList()
    return raw.map { value -> if (value.isEmpty()) null else Path.of(value).toAbsolutePath().normalize() }
  }

  fun stringList(name: String): List<String> {
    used.add(name)
    return values.get(name)?.filter { it.isNotEmpty() } ?: emptyList()
  }

  fun checkNoUnknownOptions() {
    val unknown = values.keys - used
    check(unknown.isEmpty()) { "Unknown options: ${unknown.sorted().joinToString()}" }
  }
}

private fun parseComposeArgs(args: Array<String>): ComposeOptions {
  val values = LinkedHashMap<String, MutableList<String>>()
  for (arg in args) {
    val separator = arg.indexOf('=')
    require(arg.startsWith("--") && separator > 2) { "Expected an option in the '--key=value' form, but got '$arg'" }
    val name = arg.substring(0, separator)
    values.computeIfAbsent(name) { ArrayList() }.add(arg.substring(separator + 1))
  }
  return ComposeOptions(values)
}
