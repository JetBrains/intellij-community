// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildScripts.testFramework.binaryReproducibility.BuildArtifactsReproducibilityTest
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.runtime.product.ProductMode
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
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.buildDistributions
import org.junit.jupiter.api.TestInfo
import org.opentest4j.TestAbortedException
import java.net.http.HttpConnectTimeoutException
import java.nio.file.Files
import java.nio.file.Path

fun createBuildOptionsForTest(
  productProperties: ProductProperties,
  homeDir: Path,
  skipDependencySetup: Boolean = false,
): BuildOptions {
  val outDir = createTestBuildOutDir(productProperties)
  val options = BuildOptions(
    cleanOutDir = false,
    useCompiledClassesFromProjectOutput = true,
    // todo enable once sync issues will be fixed
    jarCacheDir = homeDir.resolve("out/dev-run/jar-cache"),
  )
  customizeBuildOptionsForTest(options = options, outDir = outDir, skipDependencySetup = skipDependencySetup)
  return options
}

fun createTestBuildOutDir(productProperties: ProductProperties): Path {
  return FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).toPath()
}

private inline fun createBuildOptionsForTest(
  productProperties: ProductProperties,
  homeDir: Path,
  customizer: (BuildOptions) -> Unit,
): BuildOptions {
  val options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir)
  customizer(options)
  return options
}

fun customizeBuildOptionsForTest(options: BuildOptions, outDir: Path, skipDependencySetup: Boolean = false) {
  options.skipDependencySetup = skipDependencySetup
  options.isTestBuild = true

  options.buildStepsToSkip += listOf(
    BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP,
    BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP,
    BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP,
    BuildOptions.WIN_SIGN_STEP,
    BuildOptions.MAC_SIGN_STEP,
    BuildOptions.MAC_NOTARIZE_STEP,
  )
  options.buildUnixSnaps = false
  options.outRootDir = outDir
  options.useCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
}

suspend inline fun createBuildContext(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
): BuildContext {
  val options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homePath)
  buildOptionsCustomizer(options)
  return BuildContextImpl.createContext(
    projectHome = homePath,
    productProperties = productProperties,
    proprietaryBuildTools = buildTools,
    options = options,
  )
}

// don't expose BuildDependenciesCommunityRoot
fun runTestBuild(
  homePath: Path,
  productProperties: ProductProperties,
  traceSpanName: String,
  buildTools: ProprietaryBuildTools,
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
) {
  runTestBuild(
    homeDir = homePath,
    productProperties = productProperties,
    buildTools = buildTools,
    traceSpanName = traceSpanName,
    isReproducibilityTestAllowed = true,
    buildOptionsCustomizer = buildOptionsCustomizer,
  )
}

fun runTestBuild(
  homeDir: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
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
          projectHome = homeDir,
          productProperties = productProperties,
          proprietaryBuildTools = buildTools,
          setupTracer = false,
          options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir, customizer = buildOptionsCustomizer).also {
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
      context = BuildContextImpl.createContext(
        projectHome = homeDir,
        productProperties = productProperties,
        proprietaryBuildTools = buildTools,
        setupTracer = false,
        options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homeDir, customizer = buildOptionsCustomizer),
      ),
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