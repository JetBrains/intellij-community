// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productRunner

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.checkForNoDiskSpace
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProduct
import org.jetbrains.intellij.build.dev.configureDevModeBuildOptions
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
internal suspend fun createDevModeProductRunner(context: BuildContextImpl, additionalPluginModules: List<String> = emptyList()): IntellijProductRunner {
  var newClassPath: Collection<Path>? = null
  return checkForNoDiskSpace(context) {
    val request = BuildRequest(
      //isUnpackedDist = context.productProperties.platformPrefix != "Gateway",
      // https://youtrack.jetbrains.com/issue/IJPL-156115/devModeProductRunner-use-packed-dist-as-a-workaround-for-incorrect-product-info.json-entries-links-to-compilation-output
      isUnpackedDist = false,
      writeCoreClasspath = false,
      platformPrefix = context.productProperties.platformPrefix ?: "idea",
      baseIdePlatformPrefixForFrontend = context.productProperties.baseIdePlatformPrefixForFrontend,
      additionalModules = additionalPluginModules,
      projectDir = context.paths.projectHome,
      devRootDir = context.paths.tempDir.resolve("dev-run"),
      jarCacheDir = context.paths.projectHome.resolve("out/dev-run/jar-cache"),
      generateRuntimeModuleRepository = context.useModularLoader,
      platformClassPathConsumer = { _: String, classPath: Set<Path>, _: Path ->
        newClassPath = classPath
      },
      isBootClassPathCorrect = true,
    )
    val runDir = buildProduct(request) { buildDir ->
      createBuildContextFromExistingContext(baseContext = context, request = request, buildDir = buildDir, scope = this)
    }
    DevModeProductRunner(context = context, homePath = runDir, classPath = newClassPath!!.map { it.toString() })
  }
}

private fun createBuildContextFromExistingContext(
  baseContext: BuildContextImpl,
  request: BuildRequest,
  buildDir: Path,
  scope: CoroutineScope,
): BuildContext {
  val options = baseContext.options.copy(
    jarCacheDir = request.jarCacheDir,
    printFreeSpace = false,
    validateImplicitPlatformModule = false,
    skipDependencySetup = true,
    skipCheckOutputOfPluginModules = true,
    validateModuleStructure = false,
    cleanOutDir = false,
    outRootDir = buildDir,
    compilationLogEnabled = false,
    logDir = buildDir.resolve("log"),
    isUnpackedDist = request.isUnpackedDist,
  )
  configureDevModeBuildOptions(options = options, request = request, buildOptionsTemplate = baseContext.options)

  val buildPaths = createDevBuildPaths(projectDir = request.projectDir, buildDir = buildDir, logDir = options.logDir!!)
  val messages = BuildMessagesImpl.create()
  messages.setDebugLogPath(buildPaths.logDir.resolve("debug.log"))
  BuildMessagesHandler.initLoggingIfNeeded(messages)

  val compilationContext = normalizeCompilationContextForBuild(
    context = baseContext.compilationContext.createCopy(messages = messages, options = options, paths = buildPaths, scope = scope),
    scope = scope,
  )
  return createDevBuildContext(
    compilationContext = compilationContext,
    productProperties = baseContext.productProperties,
    request = request,
  )
}

private class DevModeProductRunner(
  private val context: BuildContext,
  private val homePath: Path,
  private val classPath: Collection<String>,
) : IntellijProductRunner {
  override suspend fun runProduct(args: List<String>, additionalVmProperties: VmProperties, timeout: Duration) {
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