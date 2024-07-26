// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
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
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.TeamCityHelper.isUnderTeamCity
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.telemetry.JaegerJsonSpanExporterManager
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.useWithScope
import org.junit.jupiter.api.TestInfo
import org.opentest4j.TestAbortedException
import java.net.http.HttpConnectTimeoutException
import java.nio.file.Files
import java.nio.file.Path

fun createBuildOptionsForTest(productProperties: ProductProperties, homeDir: Path, skipDependencySetup: Boolean = false, testInfo: TestInfo? = null): BuildOptions {
  val outDir = createTestBuildOutDir(productProperties)
  val options = BuildOptions(cleanOutDir = false, useCompiledClassesFromProjectOutput = true, jarCacheDir = homeDir.resolve("out/dev-run/jar-cache"))
  customizeBuildOptionsForTest(options = options, outDir = outDir, skipDependencySetup = skipDependencySetup, testInfo = testInfo)
  return options
}

fun createTestBuildOutDir(productProperties: ProductProperties): Path {
  return FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).toPath()
}

private inline fun createBuildOptionsForTest(productProperties: ProductProperties, homeDir: Path, testInfo: TestInfo, customizer: (BuildOptions) -> Unit): BuildOptions {
  val options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir, testInfo = testInfo)
  customizer(options)
  return options
}

fun customizeBuildOptionsForTest(options: BuildOptions, outDir: Path, skipDependencySetup: Boolean = false, testInfo: TestInfo?) {
  options.skipDependencySetup = skipDependencySetup
  options.isTestBuild = true

  options.buildStepsToSkip += listOf(
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
  val options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir)
  buildOptionsCustomizer(options)
  return BuildContextImpl.createContext(projectHome = homeDir, productProperties = productProperties, proprietaryBuildTools = buildTools, options = options)
}

// don't expose BuildDependenciesCommunityRoot
fun runTestBuild(
  homePath: Path,
  productProperties: ProductProperties,
  testInfo: TestInfo,
  buildTools: ProprietaryBuildTools,
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
) {
  runTestBuild(
    homeDir = homePath,
    productProperties = productProperties,
    buildTools = buildTools,
    testInfo = testInfo,
    isReproducibilityTestAllowed = true,
    buildOptionsCustomizer = buildOptionsCustomizer,
  )
}

fun runTestBuild(
  homeDir: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  testInfo: TestInfo,
  isReproducibilityTestAllowed: Boolean = true,
  build: suspend (BuildContext) -> Unit = { buildDistributions(it) },
  onSuccess: suspend (BuildContext) -> Unit = {},
  buildOptionsCustomizer: (BuildOptions) -> Unit = {}
) = runBlocking(Dispatchers.Default) {
  if (isReproducibilityTestAllowed && BuildArtifactsReproducibilityTest.isEnabled) {
    val reproducibilityTest = BuildArtifactsReproducibilityTest()
    repeat(reproducibilityTest.iterations) { iterationNumber ->
      launch {
        doRunTestBuild(
          context = {
            BuildContextImpl.createContext(
              projectHome = homeDir,
              productProperties = productProperties,
              proprietaryBuildTools = buildTools,
              setupTracer = false,
              options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir, testInfo, customizer = buildOptionsCustomizer).also {
                reproducibilityTest.configure(it)
              },
            )
          },
          traceSpanName = "${testInfo.spanName}#$iterationNumber",
          writeTelemetry = false,
          build = { context ->
            build(context)
            onSuccess(context)
            reproducibilityTest.iterationFinished(iterationNumber, context)
          }
        )
      }
    }
  }
  else {
    doRunTestBuild(
      context = {
        BuildContextImpl.createContext(
          projectHome = homeDir,
          productProperties = productProperties,
          proprietaryBuildTools = buildTools,
          setupTracer = false,
          options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir, testInfo, customizer = buildOptionsCustomizer),
        )
      },
      writeTelemetry = true,
      traceSpanName = testInfo.spanName,
      build = { context ->
        build(context)
        onSuccess(context)
      }
    )
  }
}

// FIXME: test reproducibility
suspend fun runTestBuild(
  testInfo: TestInfo,
  context: suspend () -> BuildContext,
  build: suspend (BuildContext) -> Unit = { buildDistributions(it) }
) {
  doRunTestBuild(context = context, traceSpanName = testInfo.spanName, writeTelemetry = true, build = build)
}

private val defaultLogFactory = Logger.getFactory()

private suspend fun doRunTestBuild(context: suspend () -> BuildContext, traceSpanName: String, writeTelemetry: Boolean, build: suspend (context: BuildContext) -> Unit) {
  var outDir: Path? = null
  var traceFile: Path? = null
  var error: Throwable? = null
  try {
    spanBuilder(traceSpanName).useWithScope { span ->
        val context = context()
        context.cleanupJarCache()
        outDir = context.paths.buildOutputDir
        span.setAttribute("outDir", "$outDir")
        if (writeTelemetry) {
          traceFile = TestLoggerFactory.getTestLogDir().resolve("${context.productProperties.baseFileName}-$traceSpanName-trace.json").also {
            JaegerJsonSpanExporterManager.setOutput(it, addShutDownHook = false)
          }
        }
        try {
          build(context)
          val jetBrainsClientMainModule = context.productProperties.embeddedJetBrainsClientMainModule
          if (jetBrainsClientMainModule != null && context.generateRuntimeModuleRepository) {
            val softly = SoftAssertions()
            RuntimeModuleRepositoryChecker.checkIntegrityOfEmbeddedProduct(jetBrainsClientMainModule, ProductMode.FRONTEND, context, softly)
            softly.assertAll()
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          if (e !is FileComparisonFailedError) {
            span.recordException(e)
          }
          span.setStatus(StatusCode.ERROR)

          copyDebugLog(context.productProperties, context.messages)

          if (ExceptionUtilRt.causedBy(e, HttpConnectTimeoutException::class.java)) {
            error = TestAbortedException("failed to load data for build scripts", e)
          }
          else {
            error = e
          }
        }
        finally {
          // close debug logging to prevent locking of the output directory on Windows
          context.messages.close()
        }
    }
  }
  finally {
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

  error?.let {
    throw it
  }
}

private fun copyDebugLog(productProperties: ProductProperties, messages: BuildMessages) {
  try {
    val debugLogFile = messages.getDebugLog()
    if (!debugLogFile.isNullOrEmpty()) {
      val targetFile = TestLoggerFactory.getTestLogDir().resolve("${productProperties.baseFileName}-test-build-debug.log")
      Files.createDirectories(targetFile.parent)
      Files.writeString(targetFile, debugLogFile)
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