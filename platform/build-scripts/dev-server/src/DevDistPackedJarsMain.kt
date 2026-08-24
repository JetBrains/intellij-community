// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistPackedJarsMain")

package org.jetbrains.intellij.build.devServer

import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dev.collectPrepackedPluginContentJars
import org.jetbrains.intellij.build.dev.copyAsDistributionFile
import org.jetbrains.intellij.build.dev.writeDevBuildComponentManifest
import java.nio.file.Files
import java.nio.file.Path

/**
 * Turns already packed `lib/` jars into a dev-distribution component, so that a distribution can consume jars nothing
 * assembled.
 *
 * The counterpart of `DevDistMain` for the jars `jvm_library` packs itself: those actions declare only the jars they
 * merge, so unlike a fragment they survive an unrelated `.iml` edit, and this turns their outputs into the
 * directory-plus-manifest shape `composeDevBuildComponents` consumes. It evaluates no product layout, reads no project
 * model and resolves no module - all it knows is which files go to `lib/` and which product and target platform they
 * belong to.
 *
 * Which of the distribution's `lib/` jars these are is the fragment's business, not this tool's: the fragment is told
 * the same list and stops packing exactly those, and it keeps reporting their core-classpath entries because deciding
 * that needs the platform layout. This component therefore declares no core classpath and no main class - see
 * [org.jetbrains.intellij.build.dev.DevBuildComponentManifest.mainClass].
 */
fun main(args: Array<String>) {
  val options = parseCommandLineOptions(args)
  val outputDir = options.requiredPath("--output-dir")
  val componentManifest = options.requiredPath("--component-manifest")
  val kind = requireNotNull(options.optional("--kind")) { "--kind is required: it names this component in the composition" }
  val platformPrefix = requireNotNull(options.optional("--platform-prefix")) { "--platform-prefix is required" }
  val os = options.optional("--os")?.let(::parseOs) ?: OsFamily.currentOs
  val arch = options.optional("--arch")?.let(::parseArch) ?: JvmArchitecture.currentJvmArch
  // Platform jars are a flat list. Plugin jars additionally carry a relation key and get their final paths from the
  // placement manifests written by JarPackager.
  val jarListFile = options.optionalPath("--jars-file")
  val pluginJarsFile = options.optionalPath("--plugin-jars-file")
  require((jarListFile == null) != (pluginJarsFile == null)) { "Exactly one of --jars-file and --plugin-jars-file is required" }
  val pluginPlacements = options.pathList("--plugin-placement")
  options.checkNoUnknownOptions()

  val count = if (jarListFile != null) {
    require(pluginPlacements.isEmpty()) { "--plugin-placement is only valid with --plugin-jars-file" }
    collectPlatformJars(jarListFile = jarListFile, outputDir = outputDir)
  }
  else {
    collectPrepackedPluginContentJars(
      pluginJarsFile = checkNotNull(pluginJarsFile),
      placementFiles = pluginPlacements,
      outputDir = outputDir,
    )
  }

  writeDevBuildComponentManifest(
    file = componentManifest,
    kind = kind,
    platformPrefix = platformPrefix,
    os = os,
    arch = arch,
    additionalModules = emptyList(),
    mainClass = null,
    coreClassPath = emptyList(),
    pluginCount = 0,
    componentRoot = outputDir,
  )
  println("Dev distribution component '$kind' collected $count packed jars into $outputDir")
}

private fun collectPlatformJars(jarListFile: Path, outputDir: Path): Int {
  val libDir = outputDir.resolve("lib")
  Files.createDirectories(libDir)
  val jars = Files.readAllLines(jarListFile).filter { it.isNotBlank() }.map { Path.of(it).toAbsolutePath().normalize() }
  require(jars.isNotEmpty()) { "$jarListFile names no jar, so this component would contribute nothing" }

  val byName = HashMap<String, Path>(jars.size)
  for (jar in jars) {
    val name = jar.fileName.toString()
    val previous = byName.put(name, jar)
    require(previous == null) { "Two packed jars are named '$name': $previous and $jar" }
    copyAsDistributionFile(source = jar, target = libDir.resolve(name))
  }
  return jars.size
}
