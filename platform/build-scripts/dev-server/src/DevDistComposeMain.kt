// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistComposeMain")
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.devIdeConfig.DevIdeConfig
import org.jetbrains.intellij.build.dev.DevBuildComponent
import org.jetbrains.intellij.build.dev.composeDevBuildComponents
import org.jetbrains.intellij.build.dev.readDevBuildComponentManifest
import org.jetbrains.intellij.build.dev.readDevBuildCompositionSpec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
  val options = parseCommandLineOptions(args)
  val specFile = options.requiredPath("--composition-spec")
  val spec = readDevBuildCompositionSpec(specFile)
  val outputDir = options.requiredPath("--output-dir")
  val ideConfig = options.requiredPath("--ide-config")
  val fingerprintFile = options.requiredPath("--fingerprint")
  options.checkNoUnknownOptions()

  val components = spec.components.map { component ->
    val manifest = readDevBuildComponentManifest(Path.of(component.manifest).toAbsolutePath().normalize())
    DevBuildComponent(
      root = Path.of(component.root).toAbsolutePath().normalize(),
      manifest = manifest,
      pluginClasspathPart = component.pluginClasspathPart?.let { Path.of(it).toAbsolutePath().normalize() },
    )
  }

  if (Files.exists(outputDir)) outputDir.deleteRecursively()
  val result = composeDevBuildComponents(
    components = components,
    target = outputDir,
    pluginClasspathPrefix = spec.pluginClasspathPrefix?.let { Path.of(it).toAbsolutePath().normalize() },
    expectedFragments = spec.expectedFragments,
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
