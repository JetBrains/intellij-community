// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistPackedJarsMain")

package org.jetbrains.intellij.build.devServer

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dev.DevBuildComponentSourcedFile
import org.jetbrains.intellij.build.dev.collectPrepackedPluginContentJars
import org.jetbrains.intellij.build.dev.writeSourcedDevBuildComponentManifest
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.file.Files
import java.nio.file.Path

/**
 * Turns already packed `lib/` jars into a dev-distribution component, so that a distribution can consume jars nothing
 * assembled.
 *
 * The counterpart of `DevDistMain` for the jars `jvm_library` packs itself: those actions declare only the jars they
 * merge, so unlike a fragment they survive an unrelated `.iml` edit, and this turns their outputs into the manifest
 * `composeDevBuildComponents` consumes. It evaluates no product layout, reads no project model and resolves no module -
 * all it knows is which files go to `lib/` and which product and target platform they belong to.
 *
 * It writes no tree, only that manifest, and the jars it names stay where their packers left them. The composer copies
 * each one straight into the distribution, so the jars are written once instead of twice - a component tree here would
 * exist for no reason other than to be copied out of again.
 *
 * Which of the distribution's `lib/` jars these are is the fragment's business, not this tool's: the fragment is told
 * the same list and stops packing exactly those, and it keeps reporting their core-classpath entries because deciding
 * that needs the platform layout. This component therefore declares no core classpath and no main class - see
 * [org.jetbrains.intellij.build.dev.DevBuildComponentManifest.mainClass].
 */
fun main(args: Array<String>) {
  val options = parseCommandLineOptions(args)
  runDevDistJob(traceFile = options.optionalPath(TRACE_FILE_OPTION), jobName = "collect packed jars") {
    collectPackedJars(options)
  }
}

private fun collectPackedJars(options: CommandLineOptions) {
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
  // the root span is what a merged timeline groups an action's spans under, and every packed-jars action opens the
  // same one, so it has to say which component it collected
  Span.current().setAttribute("kind", kind)

  val files = if (jarListFile != null) {
    require(pluginPlacements.isEmpty()) { "--plugin-placement is only valid with --plugin-jars-file" }
    collectPlatformJars(jarListFile)
  }
  else {
    collectPrepackedPluginContentJars(pluginJarsFile = checkNotNull(pluginJarsFile), placementFiles = pluginPlacements)
  }

  writeSourcedDevBuildComponentManifest(
    file = componentManifest,
    kind = kind,
    platformPrefix = platformPrefix,
    os = os,
    arch = arch,
    files = files,
  )
  println("Dev distribution component '$kind' named ${files.size} packed jars in $componentManifest")
}

/**
 * Names every jar of the list at `lib/<its own file name>`, which is the whole of this component's layout.
 *
 * The paths are kept as the list gave them - execution-root-relative - because the manifest hands them on to the
 * composer, whose own working directory is what they have to resolve against.
 */
private fun collectPlatformJars(jarListFile: Path): List<DevBuildComponentSourcedFile> {
  return spanBuilder("collect platform jars").blockingUse { span ->
    val jars = Files.readAllLines(jarListFile).filter { it.isNotBlank() }
    require(jars.isNotEmpty()) { "$jarListFile names no jar, so this component would contribute nothing" }

    var byteCount = 0L
    val byName = HashMap<String, String>(jars.size)
    val result = ArrayList<DevBuildComponentSourcedFile>(jars.size)
    for (jar in jars) {
      val name = Path.of(jar).fileName.toString()
      val previous = byName.put(name, jar)
      require(previous == null) { "Two packed jars are named '$name': $previous and $jar" }
      byteCount += Files.size(Path.of(jar))
      result.add(DevBuildComponentSourcedFile(relativePath = "lib/$name", source = jar))
    }
    span.setAttribute("jarCount", jars.size.toLong())
    span.setAttribute("byteCount", byteCount)
    result
  }
}
