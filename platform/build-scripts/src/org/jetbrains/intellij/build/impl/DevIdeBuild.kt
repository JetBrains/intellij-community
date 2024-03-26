// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProductInProcess
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal suspend fun createDevIdeBuild(context: BuildContext): DevIdeBuild {
  var newClassPath: Collection<Path>? = null
  val runDir = buildProductInProcess(
    BuildRequest(
      devRootPath = context.paths.tempDir.resolve("dev-run"),
      isUnpackedDist = true,
      platformPrefix = context.productProperties.platformPrefix ?: "idea",
      additionalModules = emptyList(),
      homePath = context.paths.projectHome,
      productionClassOutput = context.classesOutputDirectory.resolve("production"),
      platformClassPathConsumer = { classPath, _ ->
        newClassPath = classPath
      }
    )
  )
  return DevIdeBuild(context = context, homePath = runDir, classPath = newClassPath!!)
}

/**
 * Only for use in build scripts, not for dev mode / integrations tests.
 */
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