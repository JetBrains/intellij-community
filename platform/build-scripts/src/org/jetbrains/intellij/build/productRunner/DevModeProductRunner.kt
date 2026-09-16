// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productRunner

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.checkForNoDiskSpace
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProduct
import org.jetbrains.intellij.build.dev.configureDevModeBuildOptions
import org.jetbrains.intellij.build.dev.copyWithDevBuildOverrides
import org.jetbrains.intellij.build.dev.createDevBuildContext
import org.jetbrains.intellij.build.dev.createDevBuildPaths
import org.jetbrains.intellij.build.dev.readVmOptions
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.logging.BuildMessagesHandler
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.impl.normalizeCompilationContextForBuild
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Only for use in build scripts, not for dev mode / integrations tests.
 * Use [BuildContext.createProductRunner] instead of calling this function directly.
 */
internal fun createDevModeProductRunner(context: BuildContextImpl, additionalPluginModules: List<String> = emptyList()): IntellijProductRunner {
  return checkForNoDiskSpace(context) {
    val request = BuildRequest(
      writeCoreClasspath = false,
      platformPrefix = context.productProperties.platformPrefix ?: "idea",
      baseIdePlatformPrefixForFrontend = context.productProperties.baseIdePlatformPrefixForFrontend,
      additionalModules = additionalPluginModules,
      projectDir = context.paths.projectHome,
      devRootDir = context.paths.tempDir.resolve("dev-run"),
      jarCacheDir = context.paths.projectHome.resolve("out/dev-run/jar-cache"),
      generateRuntimeModuleRepository = context.useModularLoader,
      isBootClassPathCorrect = true,
    )
    val build = buildProduct(request) { buildDir, lifetime ->
      createBuildContextFromExistingContext(baseContext = context, request = request, buildDir = buildDir, lifetime = lifetime)
    }
    DevModeProductRunner(context = context, homePath = build.runDir, classPath = build.coreClassPath.map { it.toString() })
  }
}

private fun createBuildContextFromExistingContext(
  baseContext: BuildContextImpl,
  request: BuildRequest,
  buildDir: Path,
  lifetime: BuildLifetime,
): BuildContext {
  val options = baseContext.options.copyWithDevBuildOverrides(
    request = request,
    buildDir = buildDir,
    // this assembly is nested in a real build, so without an explicit override it keeps that build's date rather than the dev-mode one
    defaultBuildDateInSeconds = baseContext.options.buildDateInSeconds,
  )
  configureDevModeBuildOptions(options = options, request = request, buildOptionsTemplate = baseContext.options)

  val buildPaths = createDevBuildPaths(
    projectDir = request.projectDir,
    buildDir = buildDir,
    logDir = options.logDir!!,
    scratchDir = request.scratchDir ?: buildDir,
  )
  val messages = BuildMessagesImpl.create()
  messages.setDebugLogPath(buildPaths.logDir.resolve("debug.log"))
  BuildMessagesHandler.initLoggingIfNeeded(messages)

  val compilationContext = normalizeCompilationContextForBuild(
    context = baseContext.compilationContext.createCopy(messages = messages, options = options, paths = buildPaths, lifetime = lifetime),
    lifetime = lifetime,
  )
  return createDevBuildContext(
    compilationContext = compilationContext,
    productProperties = baseContext.productProperties,
    request = request,
    lifetime = lifetime,
  )
}

private class DevModeProductRunner(
  private val context: BuildContext,
  private val homePath: Path,
  private val classPath: Collection<String>,
) : IntellijProductRunner {
  override fun runProduct(args: List<String>, additionalVmProperties: VmProperties, timeout: Duration) {
    val vmOptionsFromBuild = readVmOptions(homePath)
    val appStarterId = args.firstOrNull() ?: "appStarter"
    doRunApplicationStarter(
      appStarterId = appStarterId,
      context = context,
      classpath = classPath,
      args = args,
      timeout = timeout,
      homePath = homePath,
      vmProperties = additionalVmProperties,
      isFinalClassPath = true,
      vmOptions = vmOptionsFromBuild,
      tempDir = Files.createTempDirectory(context.paths.tempDir, appStarterId),
    )
  }
}
