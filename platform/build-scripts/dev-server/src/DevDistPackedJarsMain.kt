// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistPackedJarsMain")

package org.jetbrains.intellij.build.devServer

import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dev.writeDevBuildComponentManifest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

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
  // One file rather than one option per jar: a product hands over hundreds of them, and the packing action passes them
  // through a Bazel param file for the same reason.
  val jarListFile = options.requiredPath("--jars-file")
  options.checkNoUnknownOptions()

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
  println("Dev distribution component '$kind' collected ${jars.size} packed jars into $outputDir")
}

/**
 * Copies [source] to [target] as an ordinary distribution file.
 *
 * `COPY_ATTRIBUTES` for the same reason the composer uses it - it selects the JDK's platform-optimized copy path and
 * copy-on-write where the file system has it - and then the mode is normalized, because it is the *source's* mode that
 * comes with it and the source is a Bazel action input, staged read-only and executable. A distribution jar is neither,
 * and `DevBuildComponentEntry.executable` feeds the IDE fingerprint: left as staged, every one of these jars would say
 * "executable" and the fingerprint would follow how Bazel stages files rather than what the distribution contains.
 *
 * A hardlink would be cheaper still and is deliberately not used: it makes the component entry and the packing action's
 * output one file, so normalizing the mode would reach back into an output every other build reads.
 */
private fun copyAsDistributionFile(source: Path, target: Path) {
  Files.copy(source.toRealPath(), target, StandardCopyOption.COPY_ATTRIBUTES)
  try {
    Files.setPosixFilePermissions(target, DISTRIBUTION_FILE_PERMISSIONS)
  }
  catch (_: UnsupportedOperationException) {
  }
}

private val DISTRIBUTION_FILE_PERMISSIONS: Set<PosixFilePermission> = EnumSet.of(
  PosixFilePermission.OWNER_READ,
  PosixFilePermission.OWNER_WRITE,
  PosixFilePermission.GROUP_READ,
  PosixFilePermission.OTHERS_READ,
)
