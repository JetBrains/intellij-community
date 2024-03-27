// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProduct
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Only for use in build scripts, not for dev mode / integrations tests.
 */
internal suspend fun createDevIdeBuild(context: BuildContext): DevIdeBuild {
  var newClassPath: Collection<Path>? = null
  val homeDir = context.paths.projectHome
  val runDir = buildProduct(
    request = BuildRequest(
      isUnpackedDist = true,
      platformPrefix = context.productProperties.platformPrefix ?: "idea",
      additionalModules = emptyList(),
      projectDir = homeDir,
      devRootDir = context.paths.tempDir.resolve("dev-run"),
      jarCacheDir = homeDir.resolve("out/dev-run/jar-cache"),
      productionClassOutput = context.classesOutputDirectory.resolve("production"),
      platformClassPathConsumer = { classPath, _ ->
        newClassPath = classPath
      },
      buildOptionsTemplate = context.options,
    ),
    createProductProperties = { context.productProperties }
  )
  return DevIdeBuild(context = context, homePath = runDir, classPath = newClassPath!!)
}

internal class DevIdeBuild(
  private val context: BuildContext,
  private val homePath: Path,
  private val classPath: Collection<Path>,
) {
  suspend fun runProduct(
    tempDir: Path,
    arguments: List<String>,
    systemProperties: Map<String, Any> = emptyMap(),
    isLongRunning: Boolean = false,
  ) {
    runApplicationStarter(
      context = context,
      tempDir = tempDir,
      ideClasspath = classPath.map { it.toString() },
      arguments = arguments,
      timeout = if (isLongRunning) DEFAULT_TIMEOUT else 30.seconds,
      homePath = homePath,
      systemProperties = systemProperties + getIdeSystemProperties(homePath),
    )
  }
}