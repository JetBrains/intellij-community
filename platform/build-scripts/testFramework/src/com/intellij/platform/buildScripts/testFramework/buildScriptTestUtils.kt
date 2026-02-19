// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildScripts.testFramework.binaryReproducibility.BuildArtifactsReproducibilityTest
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.ExceptionUtilRt
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.closeKtorClient
import org.jetbrains.intellij.build.dependencies.TeamCityHelper.isUnderTeamCity
import org.jetbrains.intellij.build.getDevModeOrTestBuildDateInSeconds
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.telemetry.JaegerJsonSpanExporterManager
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.junit.jupiter.api.TestInfo
import org.opentest4j.TestAbortedException
import java.net.http.HttpConnectTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.OnErrorResult
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists
import kotlin.io.path.name

fun createBuildOptionsForTest(
  productProperties: ProductProperties,
  homeDir: Path,
  skipDependencySetup: Boolean = false,
  testInfo: TestInfo? = null,
): BuildOptions {
  val outDir = createTestBuildOutDir(productProperties)
  val options = BuildOptions(
    cleanOutDir = false,
    //TODO: figure out what to do on bazel
    // affects org.jetbrains.intellij.build.impl.CompilationContextImpl.overrideClassesOutputDirectory
    useCompiledClassesFromProjectOutput = true,
    jarCacheDir = homeDir.resolve("out/dev-run/jar-cache"),
    buildDateInSeconds = getDevModeOrTestBuildDateInSeconds(),
  )
  customizeBuildOptionsForTest(options = options, outDir = outDir, skipDependencySetup = skipDependencySetup, testInfo = testInfo)
  return options
}

fun createTestBuildOutDir(productProperties: ProductProperties): Path {
  return Files.createTempDirectory("test-build-${productProperties.baseFileName}")
}

fun customizeBuildOptionsForTest(options: BuildOptions, outDir: Path, skipDependencySetup: Boolean = false, testInfo: TestInfo?) {
  options.skipDependencySetup = skipDependencySetup
  options.isTestBuild = true
  options.buildStepsToSkip += listOf(
    BuildOptions.LIBRARY_URL_CHECK_STEP,
    BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP,
    BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP,
    BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP,
    BuildOptions.WIN_SIGN_STEP,
    BuildOptions.MAC_SIGN_STEP,
    BuildOptions.MAC_NOTARIZE_STEP,
    BuildOptions.MAC_DMG_STEP,
  )
  options.buildUnixSnaps = false
  options.outRootDir = outDir
  options.useCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
  if (testInfo != null && isUnderTeamCity) {
    options.buildStepListener = BuildStepTeamCityListener(testInfo)
  }
}

suspend inline fun createBuildContext(
  homeDir: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
): BuildContext {
  val options = createBuildOptionsForTest(productProperties, homeDir)
  buildOptionsCustomizer(options)
  return createBuildContext(projectHome = homeDir, productProperties = productProperties, proprietaryBuildTools = buildTools, options = options)
}

fun runTestBuild(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools,
  testInfo: TestInfo,
  onSuccess: suspend (BuildContext) -> Unit = {},
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
) {
  runTestBuild(
    homeDir = homePath,
    productProperties = productProperties,
    testInfo = testInfo,
    buildTools = buildTools,
    isReproducibilityTestAllowed = true,
    onSuccess = onSuccess,
    buildOptionsCustomizer = buildOptionsCustomizer,
  )
}

fun runTestBuild(
  homeDir: Path,
  productProperties: ProductProperties,
  testInfo: TestInfo,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  isReproducibilityTestAllowed: Boolean = true,
  checkIntegrityOfEmbeddedFrontend: Boolean = true,
  checkPrivatePluginModulesAreNotPublic: Boolean = true,
  build: suspend (BuildContext) -> Unit = { buildDistributions(context = it) },
  onSuccess: suspend (BuildContext) -> Unit = {},
  buildOptionsCustomizer: (BuildOptions) -> Unit = {}
): Unit = runBlocking(Dispatchers.Default) {
  if (isReproducibilityTestAllowed && BuildArtifactsReproducibilityTest.isEnabled) {
    val reproducibilityTest = BuildArtifactsReproducibilityTest()
    repeat(reproducibilityTest.iterations) { iterationNumber ->
      launch {
        doRunTestBuild(
          context = createBuildContext(
            projectHome = homeDir,
            productProperties = productProperties,
            setupTracer = false,
            proprietaryBuildTools = buildTools,
            options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir, testInfo = testInfo).also {
              buildOptionsCustomizer(it)
              reproducibilityTest.configure(it)
            },
          ),
          traceSpanName = "${testInfo.spanName}#${iterationNumber}",
          writeTelemetry = false,
          checkIntegrityOfEmbeddedFrontend = checkIntegrityOfEmbeddedFrontend,
          checkPrivatePluginModulesAreNotPublic = checkPrivatePluginModulesAreNotPublic,
          checkThatBundledPluginInFrontendArePresent = checkIntegrityOfEmbeddedFrontend,
          build = { context ->
            build(context)
            onSuccess(context)
            reproducibilityTest.iterationFinished(iterationNumber, context)
          },
        )
      }
    }
  }
  else {
    doRunTestBuild(
      context = createBuildContext(
        projectHome = homeDir,
        productProperties = productProperties,
        setupTracer = false,
        proprietaryBuildTools = buildTools,
        options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir, testInfo = testInfo).also { buildOptionsCustomizer(it) },
        scope = this@runBlocking,
      ),
      writeTelemetry = true,
      checkIntegrityOfEmbeddedFrontend = checkIntegrityOfEmbeddedFrontend,
      checkThatBundledPluginInFrontendArePresent = checkIntegrityOfEmbeddedFrontend,
      traceSpanName = testInfo.spanName,
      build = { context ->
        build(context)
        onSuccess(context)
      },
    )
  }
}

// FIXME: test reproducibility
suspend fun runTestBuild(
  testInfo: TestInfo,
  context: suspend () -> BuildContext,
  checkThatBundledPluginInFrontendArePresent: Boolean = true,
  checkPrivatePluginModulesAreNotPublic: Boolean = true,
  build: suspend (BuildContext) -> Unit = { buildDistributions(it) }
) {
  doRunTestBuild(
    context = context(),
    traceSpanName = testInfo.spanName,
    writeTelemetry = true,
    checkIntegrityOfEmbeddedFrontend = true,
    checkThatBundledPluginInFrontendArePresent = checkThatBundledPluginInFrontendArePresent,
    checkPrivatePluginModulesAreNotPublic = checkPrivatePluginModulesAreNotPublic,
    build = build,
  )
}

private val defaultLogFactory = Logger.getFactory()

internal suspend fun <T> doRunTestBuild(
  context: BuildContext,
  traceSpanName: String,
  writeTelemetry: Boolean,
  checkIntegrityOfEmbeddedFrontend: Boolean,
  checkThatBundledPluginInFrontendArePresent: Boolean,
  checkPrivatePluginModulesAreNotPublic: Boolean = true,
  build: suspend (context: BuildContext) -> T,
): T {
  var outDir: Path? = null
  var traceFile: Path? = null
  val buildLogsDir = TestLoggerFactory.getTestLogDir().resolve("${context.productProperties.baseFileName}-$traceSpanName")
  Logger.setFactory(TestLoggerFactory::class.java)
  try {
    return spanBuilder(traceSpanName).use { span ->
      context.cleanupJarCache()
      outDir = context.paths.buildOutputDir
      span.setAttribute("outDir", outDir.toString())
      if (writeTelemetry) {
        traceFile = buildLogsDir.resolve("trace.json").also {
          JaegerJsonSpanExporterManager.setOutput(file = it, addShutDownHook = false)
        }
      }
      try {
        val result = build(context)

        val softly = SoftAssertions()
        if (checkIntegrityOfEmbeddedFrontend) {
          val frontendRootModule = context.productProperties.embeddedFrontendRootModule
          if (frontendRootModule != null && context.generateRuntimeModuleRepository) {
            RuntimeModuleRepositoryChecker.checkProductModules(productModulesModule = frontendRootModule, context = context, softly = softly)
            if (checkThatBundledPluginInFrontendArePresent) {
              RuntimeModuleRepositoryChecker.checkBundledPluginsArePresent(productModulesModule = frontendRootModule, context = context, isEmbeddedVariant = true, softly = softly)
            }
            RuntimeModuleRepositoryChecker.checkIntegrityOfEmbeddedFrontend(frontendRootModule, context, softly)
            checkKeymapPluginsAreBundledWithFrontend(frontendRootModule, context, softly)
          }
        }

        if (checkPrivatePluginModulesAreNotPublic) {
          checkPrivatePluginModulesAreNotPublic(context, softly)
        }
        softly.assertAll()

        result
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        if (e !is FileComparisonFailedError) {
          span.recordException(e)
        }
        span.setStatus(StatusCode.ERROR)

        copyLogs(context, buildLogsDir)

        if (ExceptionUtilRt.causedBy(e, HttpConnectTimeoutException::class.java)) {
          throw TestAbortedException("failed to load data for build scripts", e)
        }
        else {
          throw e
        }
      }
    }
  }
  finally {
    // close debug logging to prevent locking of the output directory on Windows
    context.messages.close()

    closeKtorClient()

    if (traceFile != null) {
      TraceManager.shutdown()
      println("Performance report is written to $traceFile")
    }

    /**
     * Overridden in [org.jetbrains.intellij.build.impl.JpsCompilationRunner.runBuild]
     */
    Logger.setFactory(defaultLogFactory)

    try {
      outDir?.also(NioFiles::deleteRecursively)
    }
    catch (e: Throwable) {
      System.err.println("cannot cleanup $outDir:")
      e.printStackTrace(System.err)
    }
  }
}

private fun checkKeymapPluginsAreBundledWithFrontend(
  jetBrainsClientMainModule: String,
  context: BuildContext,
  softly: SoftAssertions,
) {
  val productModules = context.loadRawProductModules(jetBrainsClientMainModule, ProductMode.FRONTEND)
  val keymapPluginModulePrefix = "intellij.keymap."
  val keymapPluginsBundledWithFrontend = productModules.bundledPluginMainModules
    .map { it.stringId }
    .filter { it.startsWith(keymapPluginModulePrefix) }
  val keymapPluginsBundledWithMonolith = context.getBundledPluginModules().filter { it.startsWith(keymapPluginModulePrefix) }
  softly.assertThat(keymapPluginsBundledWithFrontend)
    .describedAs("Frontend variant of ${context.applicationInfo.productNameWithEdition} must bundle the same keymap plugins as the full IDE for consistency. " +
                 "Change 'bundled-plugins' in 'META-INF/$jetBrainsClientMainModule/product-modules.xml' to fix this.")
    .containsExactlyInAnyOrder(*keymapPluginsBundledWithMonolith.toTypedArray())
}

@OptIn(ExperimentalPathApi::class)
private fun copyLogs(context: BuildContext, buildLogsDir: Path) {
  try {
    if (context.paths.logDir.exists()) {
      Files.createDirectories(buildLogsDir)
      context.paths.logDir.copyToRecursively(buildLogsDir, followLinks = false, onError = { source, target, exception ->
        Span.current().addEvent("failed to copy log file: ${source.name} -> ${target.name}: ${exception.message}")
        OnErrorResult.SKIP_SUBTREE
      })
    }
    
    val debugLogText = context.messages.getDebugLog()
    if (!debugLogText.isNullOrEmpty()) {
      val targetFile = buildLogsDir.resolve("test-build-debug.log")
      Files.writeString(targetFile, debugLogText)
      Span.current().addEvent("debug log copied to $targetFile")
    }
  }
  catch (e: Throwable) {
    Span.current().addEvent("failed to copy debug log: ${e.message}")
    e.printStackTrace(System.err)
  }
}

val TestInfo.spanName: String
  get() = "${testClass.get().simpleName}.${testMethod.orElse(null)?.name ?: "unknown"}"
