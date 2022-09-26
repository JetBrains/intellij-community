// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.rt.execution.junit.FileComparisonData
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.ExceptionUtil
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.doRunTestBuild
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.testFramework.binaryReproducibility.BuildArtifactsReproducibilityTest
import org.opentest4j.TestAbortedException
import java.net.http.HttpConnectTimeoutException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Supplier

private val initializeTracer by lazy {
  val endpoint = System.getenv("JAEGER_ENDPOINT")
  if (endpoint != null) {
    val defaultExporters = TracerProviderManager.spanExporterProvider.get()
    TracerProviderManager.spanExporterProvider = Supplier {
      defaultExporters + JaegerGrpcSpanExporter.builder().setEndpoint(endpoint).build()
    }
  }
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
  ))
  options.buildMacArtifactsWithRuntime = false
  options.buildMacArtifactsWithoutRuntime = false
  options.buildUnixSnaps = false
  options.outputRootPath = FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).toPath()
  options.useCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
}

suspend fun createBuildContext(
  homePath: Path, productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  communityHomePath: BuildDependenciesCommunityRoot,
  skipDependencySetup: Boolean = false,
  buildOptionsCustomizer: (BuildOptions) -> Unit = {},
): BuildContext {
  val options = BuildOptions()
  options.signNativeFiles = false
  options.compressZipFiles = false
  customizeBuildOptionsForTest(options, productProperties, skipDependencySetup)
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
                 buildTools: ProprietaryBuildTools) {
  runTestBuild(homePath, productProperties, buildTools, traceSpanName = null)
}

fun runTestBuild(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  communityHomePath: BuildDependenciesCommunityRoot = BuildDependenciesCommunityRoot(homePath.resolve("community")),
  traceSpanName: String? = null,
  onFinish: suspend (context: BuildContext) -> Unit = {},
  buildOptionsCustomizer: (BuildOptions) -> Unit = {}
) {
  val buildArtifactsReproducibilityTest = BuildArtifactsReproducibilityTest()
  if (!buildArtifactsReproducibilityTest.isEnabled) {
    testBuild(homePath, productProperties, buildTools, communityHomePath, traceSpanName, onFinish, buildOptionsCustomizer)
  }
  else {
    testBuild(homePath, productProperties, buildTools, communityHomePath, traceSpanName, buildOptionsCustomizer = {
      buildOptionsCustomizer(it)
      buildArtifactsReproducibilityTest.configure(it)
    }, onFinish = { firstIteration ->
      onFinish(firstIteration)
      testBuild(homePath, productProperties, buildTools, communityHomePath, traceSpanName, buildOptionsCustomizer = {
        buildOptionsCustomizer(it)
        buildArtifactsReproducibilityTest.configure(it)
      }, onFinish = { nextIteration ->
        onFinish(nextIteration)
        buildArtifactsReproducibilityTest.compare(firstIteration, nextIteration)
      })
    })
  }
}

private fun testBuild(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools,
  communityHomePath: BuildDependenciesCommunityRoot,
  traceSpanName: String?,
  onFinish: suspend (context: BuildContext) -> Unit,
  buildOptionsCustomizer: (BuildOptions) -> Unit,
) {
  val context = runBlocking(Dispatchers.Default) {
    createBuildContext(
      homePath = homePath,
      productProperties = productProperties,
      buildTools = buildTools,
      skipDependencySetup = false,
      communityHomePath = communityHomePath,
      buildOptionsCustomizer = buildOptionsCustomizer,
    )
  }

  runTestBuild(
    context = context,
    traceSpanName = traceSpanName,
    onFinish = onFinish,
  )
}

// FIXME: test reproducibility
fun runTestBuild(
  context: BuildContext,
  traceSpanName: String? = null,
  onFinish: suspend (context: BuildContext) -> Unit = {},
) {
  initializeTracer

  val productProperties = context.productProperties

  // to see in Jaeger as a one trace
  val traceFileName = "${productProperties.baseFileName}-trace.json"
  val span = TraceManager.spanBuilder(traceSpanName ?: "test build of ${productProperties.baseFileName}").startSpan()
  var spanEnded = false
  val spanScope = span.makeCurrent()

  try {
    val outDir = context.paths.buildOutputDir
    span.setAttribute("outDir", outDir.toString())
    val messages = context.messages as BuildMessagesImpl
    try {
      runBlocking(Dispatchers.Default) {
        doRunTestBuild(context)
        if (context.options.targetOs == OsFamily.ALL) {
          buildDistributions(context)
        }
        else {
          onFinish(context)
        }
      }
    }
    catch (e: Throwable) {
      if (e !is FileComparisonData) {
        span.recordException(e)
      }
      span.setStatus(StatusCode.ERROR)

      copyDebugLog(productProperties, messages)

      if (ExceptionUtil.causedBy(e, HttpConnectTimeoutException::class.java)) {
        //todo use com.intellij.platform.testFramework.io.ExternalResourcesChecker after next update of jps-bootstrap library
        throw TestAbortedException("failed to load data for build scripts", e)
      }
      else {
        throw e
      }
    }
    finally {
      // redirect debug logging to some other file to prevent locking of output directory on Windows
      val newDebugLog = FileUtil.createTempFile("debug-log-", ".log", true)
      messages.setDebugLogPath(newDebugLog.toPath())

      spanScope.close()
      span.end()
      spanEnded = true
      copyPerfReport(traceFileName)

      try {
        NioFiles.deleteRecursively(outDir)
      }
      catch (e: Throwable) {
        System.err.println("cannot cleanup $outDir:")
        e.printStackTrace(System.err)
      }
    }
  }
  finally {
    if (!spanEnded) {
      spanScope.close()
      span.end()
    }
  }
}

private fun copyDebugLog(productProperties: ProductProperties, messages: BuildMessagesImpl) {
  try {
    val targetFile = TestLoggerFactory.getTestLogDir().resolve("${productProperties.baseFileName}-test-build-debug.log")
    Files.createDirectories(targetFile.parent)
    Files.copy(messages.debugLogFile!!, targetFile, StandardCopyOption.REPLACE_EXISTING)
    messages.info("Debug log copied to $targetFile")
  }
  catch (e: Throwable) {
    messages.warning("Failed to copy debug log: ${e.message}")
  }
}

private fun copyPerfReport(traceFileName: String) {
  val targetFile = TestLoggerFactory.getTestLogDir().resolve(traceFileName)
  Files.createDirectories(targetFile.parent)
  val file = TraceManager.finish() ?: return
  try {
    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
    println("Performance report is written to $targetFile")
  }
  catch (ignore: NoSuchFileException) {
  }
  catch (e: Throwable) {
    System.err.println("cannot write performance report: ${e.message}")
  }
}