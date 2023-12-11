// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildScripts.testFramework.binaryReproducibility.BuildArtifactsReproducibilityTest
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.runtime.repository.ProductMode
import com.intellij.rt.execution.junit.FileComparisonData
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
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.buildDistributions
import org.junit.jupiter.api.TestInfo
import org.opentest4j.TestAbortedException
import java.net.http.HttpConnectTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

fun createBuildOptionsForTest(productProperties: ProductProperties, skipDependencySetup: Boolean = false): BuildOptions {
  val options = BuildOptions()
  customizeBuildOptionsForTest(options = options, productProperties = productProperties, skipDependencySetup = skipDependencySetup)
  return options
}

private inline fun createBuildOptionsForTest(productProperties: ProductProperties, customizer: (BuildOptions) -> Unit): BuildOptions {
  val options = createBuildOptionsForTest(productProperties = productProperties)
  customizer(options)
  return options
}

fun customizeBuildOptionsForTest(options: BuildOptions, productProperties: ProductProperties, skipDependencySetup: Boolean = false) {
  options.skipDependencySetup = skipDependencySetup
  options.isTestBuild = true
  options.buildStepsToSkip.addAll(listOf(
    BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP,
    BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP,
    BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP,
    BuildOptions.WIN_SIGN_STEP,
    BuildOptions.MAC_SIGN_STEP,
    BuildOptions.MAC_NOTARIZE_STEP,
  ))
  options.buildUnixSnaps = false
  options.outputRootPath = FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).toPath()
  options.useCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
}

suspend inline fun createBuildContext(
  homePath: Path, productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  communityHomePath: BuildDependenciesCommunityRoot,
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
): BuildContext {
  val options = createBuildOptionsForTest(productProperties)
  buildOptionsCustomizer(options)
  return BuildContextImpl.createContext(communityHome = communityHomePath,
                                        projectHome = homePath,
                                        productProperties = productProperties,
                                        proprietaryBuildTools = buildTools,
                                        options = options)
}

// don't expose BuildDependenciesCommunityRoot
fun runTestBuild(homePath: Path,
                 productProperties: ProductProperties,
                 traceSpanName: String,
                 buildTools: ProprietaryBuildTools,
                 buildOptionsCustomizer: (BuildOptions) -> Unit = {}) {
  runTestBuild(homePath = homePath,
               productProperties = productProperties,
               buildTools = buildTools,
               traceSpanName = traceSpanName,
               isReproducibilityTestAllowed = true,
               buildOptionsCustomizer = buildOptionsCustomizer)
}

fun runTestBuild(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  communityHomePath: BuildDependenciesCommunityRoot = BuildDependenciesCommunityRoot(homePath.resolve("community")),
  traceSpanName: String,
  isReproducibilityTestAllowed: Boolean = true,
  build: suspend (context: BuildContext) -> Unit = { buildDistributions(it) },
  onSuccess: suspend (context: BuildContext) -> Unit = {},
  buildOptionsCustomizer: (BuildOptions) -> Unit = {}
) = runBlocking(Dispatchers.Default) {
  if (isReproducibilityTestAllowed) {
    val reproducibilityTest = BuildArtifactsReproducibilityTest()
    repeat(reproducibilityTest.iterations) { iterationNumber ->
      launch {
        val buildContext = BuildContextImpl.createContext(
          communityHome = communityHomePath,
          projectHome = homePath,
          productProperties = productProperties,
          proprietaryBuildTools = buildTools,
          setupTracer = false,
          options = createBuildOptionsForTest(productProperties, buildOptionsCustomizer).also {
            reproducibilityTest.configure(it)
          },
        )
        doRunTestBuild(
          context = buildContext,
          traceSpanName = "#$iterationNumber",
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
      context = BuildContextImpl.createContext(communityHome = communityHomePath,
                                               projectHome = homePath,
                                               productProperties = productProperties,
                                               proprietaryBuildTools = buildTools,
                                               setupTracer = false,
                                               options = createBuildOptionsForTest(productProperties, buildOptionsCustomizer)),
      writeTelemetry = true,
      traceSpanName = traceSpanName,
      build = { context ->
        build(context)
        onSuccess(context)
      }
    )
  }
}

// FIXME: test reproducibility
suspend fun runTestBuild(
  context: BuildContext,
  traceSpanName: String,
  build: suspend (context: BuildContext) -> Unit = { buildDistributions(it) }
) {
  doRunTestBuild(context = context, traceSpanName = traceSpanName, writeTelemetry = true, build = build)
}

private val defaultLogFactory = Logger.getFactory()

private suspend fun doRunTestBuild(context: BuildContext,
                                   traceSpanName: String?,
                                   writeTelemetry: Boolean,
                                   build: suspend (context: BuildContext) -> Unit) {
  val traceFile = if (writeTelemetry) {
    val traceFile = TestLoggerFactory.getTestLogDir().resolve("${context.productProperties.baseFileName}-$traceSpanName-trace.json")
    JaegerJsonSpanExporterManager.setOutput(traceFile, addShutDownHook = false)
    traceFile
  }
  else {
    null
  }

  val outDir = context.paths.buildOutputDir
  var error: Throwable? = null
  try {
    spanBuilder(traceSpanName ?: "test build of ${context.productProperties.baseFileName}")
      .setAttribute("outDir", outDir.toString())
      .useWithScope { span ->
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
          if (e !is FileComparisonData) {
            span.recordException(e)
          }
          span.setStatus(StatusCode.ERROR)

          copyDebugLog(context.productProperties, context.messages)

          if (ExceptionUtilRt.causedBy(e, HttpConnectTimeoutException::class.java)) {
            //todo use com.intellij.platform.testFramework.io.ExternalResourcesChecker after next update of jps-bootstrap library
            error = TestAbortedException("failed to load data for build scripts", e)
          }
          else {
            error = e
          }
        }
      }
  }
  finally {
    closeKtorClient()

    if (traceFile != null) {
      TraceManager.shutdown()
      println("Performance report is written to $traceFile")
    }

    // close debug logging to prevent locking of output directory on Windows
    context.messages.close()

    /**
     * Overridden in [org.jetbrains.intellij.build.impl.JpsCompilationRunner.runBuild]
     */
    Logger.setFactory(defaultLogFactory)

    try {
      NioFiles.deleteRecursively(outDir)
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
    val targetFile = TestLoggerFactory.getTestLogDir().resolve("${productProperties.baseFileName}-test-build-debug.log")
    Files.createDirectories(targetFile.parent)
    val debugLogFile = messages.debugLogFile
    if (debugLogFile != null && Files.exists(debugLogFile)) {
      Files.copy(debugLogFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
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